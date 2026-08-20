package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.application.port.out.IdempotencyStore;
import com.elicatari.dteissuer.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaIdempotencyStore implements IdempotencyStore {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Inserta; si choca la unicidad, relee. {@code ON CONFLICT DO NOTHING} evita
     * abortar la transacción de Postgres (un {@code INSERT} que falla deja el TX
     * inutilizable).
     */
    @Override
    @Transactional
    public Optional<IdempotencyRecord> tryClaim(TenantId tenantId, IdempotencyKey key, String requestHash) {
        TenantSession.bind(entityManager, tenantId);
        int inserted = entityManager
                .createNativeQuery(
                        "insert into idempotency_keys (tenant_id, idempotency_key, request_hash, dte_id) "
                                + "values (:tenantId, :key, :hash, null) "
                                + "on conflict (tenant_id, idempotency_key) do nothing")
                .setParameter("tenantId", tenantId.value())
                .setParameter("key", key.value())
                .setParameter("hash", requestHash)
                .executeUpdate();
        if (inserted == 1) {
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