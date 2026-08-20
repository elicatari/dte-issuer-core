package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.IssueDteUseCase;
import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.shared.TenantContext;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Frontera transaccional: DTE, cursor de folio y clave se persisten juntos.
 * El tenant queda en {@link TenantContext} antes de abrir la TX para que RLS
 * reciba {@code app.tenant_id}.
 */
@Service
public class TransactionalIssueDteUseCase {

    private final IssueDteUseCase useCase;
    private final TransactionTemplate transactionTemplate;

    TransactionalIssueDteUseCase(IssueDteUseCase useCase, TransactionTemplate transactionTemplate) {
        this.useCase = useCase;
        this.transactionTemplate = transactionTemplate;
    }

    public Dte execute(IssueDteCommand command) {
        boolean ownsContext = TenantContext.current().isEmpty();
        if (ownsContext) {
            TenantContext.set(command.tenantId());
        } else if (!TenantContext.require().equals(command.tenantId())) {
            throw new IllegalStateException("el tenant del comando no coincide con el contexto");
        }
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> useCase.execute(command)));
        } finally {
            if (ownsContext) {
                TenantContext.clear();
            }
        }
    }
}