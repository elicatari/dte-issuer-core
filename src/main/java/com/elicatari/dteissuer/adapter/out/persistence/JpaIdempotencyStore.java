package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.application.port.out.IdempotencyStore;
import com.elicatari.dteissuer.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaIdempotencyStore implements IdempotencyStore {

    @PersistenceContext
    private EntityManager entityManager;

    private final Clock clock;
    private final IdempotencyProperties properties;

    JpaIdempotencyStore(Clock clock, IdempotencyProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Inserta; si choca la unicidad, relee. {@code ON CONFLICT} no aborta la TX.
     * Una reserva en curso más vieja que el TTL y con el mismo hash se reclama
     * aquí, sin proceso de limpieza aparte.
     */
    @Override
    @Transactional
    public Optional<IdempotencyRecord> tryClaim(TenantId tenantId, IdempotencyKey key, String requestHash) {
        TenantSession.bind(entityManager, tenantId);
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofMillis(properties.inProgressTtlMs()));
        int claimed = entityManager
                .createNativeQuery(
                        "insert into idempotency_keys (tenant_id, idempotency_key, request_hash, dte_id, created_at) "
                                + "values (:tenantId, :key, :hash, null, :now) "
                                + "on conflict (tenant_id, idempotency_key) do update "
                                + "set created_at = excluded.created_at "
                                + "where idempotency_keys.dte_id is null "
                                + "and idempotency_keys.created_at <= :cutoff "
                                + "and idempotency_keys.request_hash = excluded.request_hash")
                .setParameter("tenantId", tenantId.value())
                .setParameter("key", key.value())
                .setParameter("hash", requestHash)
                .setParameter("now", now)
                .setParameter("cutoff", cutoff)
                .executeUpdate();
        if (claimed == 1) {
            return Optional.empty();
        }
        return Optional.of(loadExisting(tenantId, key));
    }

    @Override
    @Transactional
    public void complete(TenantId tenantId, IdempotencyKey key, UUID dteId) {
        TenantSession.bind(entityManager, tenantId);
        int updated = entityManager
                .createQuery(
                        "update IdempotencyKeyEntity k set k.dteId = :dteId "
                                + "where k.pk.tenantId = :tenantId and k.pk.key = :key")
                .setParameter("dteId", dteId)
                .setParameter("tenantId", tenantId.value())
                .setParameter("key", key.value())
                .executeUpdate();
        if (updated != 1) {
            throw new IllegalStateException("no se pudo completar la clave de idempotencia");
        }
    }

    private IdempotencyRecord loadExisting(TenantId tenantId, IdempotencyKey key) {
        IdempotencyKeyEntity entity = entityManager
                .createQuery(
                        "select k from IdempotencyKeyEntity k "
                                + "where k.pk.tenantId = :tenantId and k.pk.key = :key",
                        IdempotencyKeyEntity.class)
                .setParameter("tenantId", tenantId.value())
                .setParameter("key", key.value())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("conflicto de unicidad sin fila de idempotencia"));
        return PersistenceMapper.toRecord(entity);
    }
}