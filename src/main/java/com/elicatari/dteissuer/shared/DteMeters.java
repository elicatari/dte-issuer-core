package com.elicatari.dteissuer.shared;

import com.elicatari.dteissuer.domain.DocumentType;
import com.elicatari.dteissuer.domain.TenantId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Nombres de métricas de emisión. Cardinalidad acotada: {@code tenant_id} y
 * {@code document_type}. Nunca folio ni RUT.
 */
public class DteMeters {

    public static final String ISSUED = "dte.issued";
    public static final String FOLIO_RESERVATION = "dte.folio.reservation";
    public static final String OUTBOX_PENDING = "dte.outbox.pending";
    public static final String OUTBOX_LAG = "dte.outbox.lag";

    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_DOCUMENT_TYPE = "document_type";

    private final MeterRegistry registry;

    public DteMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordIssued(TenantId tenantId, DocumentType documentType) {
        Counter.builder(ISSUED)
                .description("DTE emitidos (no incluye replay de idempotencia)")
                .tag(TAG_TENANT_ID, tenantId.value())
                .tag(TAG_DOCUMENT_TYPE, documentType.name())
                .register(registry)
                .increment();
    }

    public Timer.Sample startFolioReservation() {
        return Timer.start(registry);
    }

    public void stopFolioReservation(Timer.Sample sample, TenantId tenantId) {
        sample.stop(Timer.builder(FOLIO_RESERVATION)
                .description("Espera del lock pesimista del rango de folio")
                .tag(TAG_TENANT_ID, tenantId.value())
                .register(registry));
    }
}