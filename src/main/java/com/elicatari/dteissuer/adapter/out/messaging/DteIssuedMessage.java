package com.elicatari.dteissuer.adapter.out.messaging;

import com.elicatari.dteissuer.domain.DteIssued;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo JSON de {@code dte.issued}. {@code tenant_id} como en el hecho de dominio.
 */
public record DteIssuedMessage(
        UUID eventId,
        Instant occurredAt,
        String tenant_id,
        UUID dteId,
        long folio,
        String rut) {

    public static DteIssuedMessage from(DteIssued event) {
        return new DteIssuedMessage(
                event.eventId(),
                event.occurredAt(),
                event.tenantId().value(),
                event.dteId(),
                event.folio().value(),
                event.rut().value());
    }
}