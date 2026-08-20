package com.elicatari.dteissuer.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho de negocio: se emitió un DTE. El descuento de folio es el efecto, no este evento.
 */
public record DteIssued(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UUID dteId,
        Folio folio,
        Rut rut) {

    public DteIssued {
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
        Objects.requireNonNull(tenantId, "tenant_id es obligatorio");
        Objects.requireNonNull(dteId, "dteId es obligatorio");
        Objects.requireNonNull(folio, "folio es obligatorio");
        Objects.requireNonNull(rut, "rut es obligatorio");
    }
}