package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaDteRepository implements DteRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Dte save(Dte dte) {
        TenantSession.bind(entityManager, dte.tenantId());
        entityManager.persist(PersistenceMapper.toEntity(dte));
        entityManager.flush();
        return dte;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dte> findById(TenantId tenantId, UUID id) {
        TenantSession.bind(entityManager, tenantId);
        // Query con tenant_id: EntityManager.find() no aplica el @Filter.
        List<DteEntity> found = entityManager
                .createQuery(
                        "select d from DteEntity d where d.id = :id and d.tenantId = :tenantId", DteEntity.class)
                .setParameter("id", id)
                .setParameter("tenantId", tenantId.value())
                .getResultList();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DteEntity entity = found.getFirst();
        if (!tenantId.value().equals(entity.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(PersistenceMapper.toDomain(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dte> findByTenantId(TenantId tenantId) {
        TenantSession.bind(entityManager, tenantId);
        return entityManager
                .createQuery(
                        "select d from DteEntity d where d.tenantId = :tenantId order by d.folio",
                        DteEntity.class)
                .setParameter("tenantId", tenantId.value())
                .getResultList()
                .stream()
                .filter(entity -> tenantId.value().equals(entity.tenantId()))
                .map(PersistenceMapper::toDomain)
                .toList();
    }
}