package com.elicatari.dteissuer.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Boleta 39 emitida. Inmutable: sin folio no se construye; anular queda fuera del recorte.
 */
public record Dte(
        UUID id,
        TenantId tenantId,
        Folio folio,
        Rut rut,
        DocumentType documentType,
        Money neto,
        Money iva,
        Money total,
        DteStatus status,
        Instant issuedAt,
        DteIssued issuedEvent) {

    public Dte {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(tenantId, "tenant_id es obligatorio");
        Objects.requireNonNull(folio, "folio es obligatorio");
        Objects.requireNonNull(rut, "rut es obligatorio");
        Objects.requireNonNull(documentType, "tipo documental es obligatorio");
        Objects.requireNonNull(neto, "neto es obligatorio");
        Objects.requireNonNull(iva, "iva es obligatorio");
        Objects.requireNonNull(total, "total es obligatorio");
        Objects.requireNonNull(status, "estado es obligatorio");
        Objects.requireNonNull(issuedAt, "issuedAt es obligatorio");
        Objects.requireNonNull(issuedEvent, "evento es obligatorio");
        if (documentType != DocumentType.BOLETA_39) {
            throw new IllegalArgumentException("solo se emite Boleta 39");
        }
        if (status != DteStatus.ISSUED) {
            throw new IllegalArgumentException("el DTE emitido queda en estado issued");
        }
        if (!neto.isPositive()) {
            throw new IllegalArgumentException("el neto debe ser mayor a 0");
        }
        if (!iva.equals(neto.iva19())) {
            throw new IllegalArgumentException("el IVA debe ser el 19% del neto, redondeado a peso");
        }
        if (!total.equals(neto.plus(iva))) {
            throw new IllegalArgumentException("el total debe ser neto + IVA");
        }
        if (!issuedEvent.dteId().equals(id)
                || !issuedEvent.tenantId().equals(tenantId)
                || !issuedEvent.folio().equals(folio)
                || !issuedEvent.rut().equals(rut)
                || !issuedEvent.occurredAt().equals(issuedAt)) {
            throw new IllegalArgumentException("el evento DteIssued no coincide con el documento");
        }
    }

    /**
     * Emite una Boleta 39. El folio ya tuvo que salir de {@link FolioRange#reserveNext()}.
     */
    public static Dte issue(TenantId tenantId, Folio folio, Rut rut, Money neto, Instant issuedAt) {
        Objects.requireNonNull(tenantId, "tenant_id es obligatorio");
        Objects.requireNonNull(folio, "sin folio no hay DTE");
        Objects.requireNonNull(rut, "el RUT es obligatorio");
        Objects.requireNonNull(neto, "el neto es obligatorio");
        Objects.requireNonNull(issuedAt, "issuedAt es obligatorio");
        UUID id = UUID.randomUUID();
        Money iva = neto.iva19();
        Money total = neto.plus(iva);
        DteIssued event = new DteIssued(UUID.randomUUID(), issuedAt, tenantId, id, folio, rut);
        return new Dte(
                id,
                tenantId,
                folio,
                rut,
                DocumentType.BOLETA_39,
                neto,
                iva,
                total,
                DteStatus.ISSUED,
                issuedAt,
                event);
    }
}