package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;

/**
 * Activa el {@code @Filter} y fija {@code app.tenant_id} (RLS) con el tenant del
 * contexto. Sin tenant, falla cerrada. El puerto además pasa {@code tenant_id} en
 * la query: el filtro no cubre {@code find()}.
 */
final class TenantSession {

    private TenantSession() {}

    static void bind(EntityManager entityManager, TenantId tenantId) {
        TenantId current = TenantContext.require();
        if (!current.equals(tenantId)) {
            throw new IllegalStateException(
                    "tenant del contexto (%s) no coincide con el del puerto (%s)"
                            .formatted(current.value(), tenantId.value()));
        }
        entityManager
                .unwrap(Session.class)
                .doWork(connection -> TenantRls.bind(connection, tenantId));
        entityManager
                .unwrap(Session.class)
                .enableFilter(TenantFilters.NAME)
                .setParameter(TenantFilters.PARAM, tenantId.value());
    }
}