package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.port.out.FolioRangeRepository;
import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.DteMeters;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaFolioRangeRepository implements FolioRangeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final DteMeters meters;

    JpaFolioRangeRepository(DteMeters meters) {
        this.meters = meters;
    }

    /**
     * Carga el rango con {@code SELECT ... FOR UPDATE}. Serializa por tenant:
     * Alpha no bloquea a Beta. No usa {@code MAX(folio)+1}.
     */
    @Override
    @Transactional
    public Optional<FolioRange> findByTenantId(TenantId tenantId) {
        TenantSession.bind(entityManager, tenantId);
        FolioRangeEntity entity = lockRange(tenantId);
        return entity == null ? Optional.empty() : Optional.of(PersistenceMapper.toDomain(entity));
    }

    @Override
    @Transactional
    public FolioRange save(FolioRange range) {
        TenantSession.bind(entityManager, range.tenantId());
        FolioRangeEntity entity = lockRange(range.tenantId());
        if (entity == null || !entity.id().equals(range.id())) {
            throw new IllegalStateException("no hay rango de folio para el tenant " + range.tenantId().value());
        }
        if (!range.tenantId().value().equals(entity.tenantId())) {
            throw new IllegalStateException("el rango no pertenece al tenant");
        }
        entity.setNextFolio(range.next().value());
        return PersistenceMapper.toDomain(entity);
    }

    private FolioRangeEntity lockRange(TenantId tenantId) {
        Timer.Sample sample = meters.startFolioReservation();
        try {
            List<FolioRangeEntity> rows = entityManager
                    .createQuery(
                            "select f from FolioRangeEntity f where f.tenantId = :tenantId",
                            FolioRangeEntity.class)
                    .setParameter("tenantId", tenantId.value())
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint("jakarta.persistence.lock.timeout", 5_000)
                    .getResultList();
            return rows.isEmpty() ? null : rows.getFirst();
        } finally {
            meters.stopFolioReservation(sample, tenantId);
        }
    }
}