package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.domain.DocumentType;
import com.elicatari.dteissuer.domain.DteIssued;
import com.elicatari.dteissuer.shared.DteMeters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cuenta emisiones reales. El replay de idempotencia no publica {@link DteIssued}.
 */
@Component
class DteIssuedMetricsListener {

    private final DteMeters meters;

    DteIssuedMetricsListener(DteMeters meters) {
        this.meters = meters;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onIssued(DteIssued event) {
        meters.recordIssued(event.tenantId(), DocumentType.BOLETA_39);
    }
}