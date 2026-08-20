package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.shared.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Punto único al abrir la transacción: lee {@link TenantContext}, nunca el request.
 * Sin tenant, no toca la GUC: RLS devuelve cero filas (falla cerrada).
 */
final class TenantRlsTransactionListener implements TransactionExecutionListener {

    private final EntityManagerFactory entityManagerFactory;

    TenantRlsTransactionListener(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure != null) {
            return;
        }
        TenantContext.current().ifPresent(tenantId -> {
            EntityManager entityManager =
                    EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            if (entityManager == null) {
                return;
            }
            entityManager.unwrap(Session.class).doWork(connection -> TenantRls.bind(connection, tenantId));
        });
    }
}