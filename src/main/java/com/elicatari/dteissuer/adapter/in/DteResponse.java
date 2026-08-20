package com.elicatari.dteissuer.adapter.in;

import com.elicatari.dteissuer.domain.Dte;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación HTTP de una Boleta 39 emitida. IVA y total los calculó el dominio.
 */
public record DteResponse(
        UUID id,
        String tenantId,
        long folio,
        String rut,
        String documentType,
        long neto,
        long iva,
        long total,
        String status,
        Instant issuedAt) {

    static DteResponse from(Dte dte) {
        return new DteResponse(
                dte.id(),
                dte.tenantId().value(),
                dte.folio().value(),
                dte.rut().value(),
                dte.documentType().name(),
                dte.neto().amount().longValueExact(),
                dte.iva().amount().longValueExact(),
                dte.total().amount().longValueExact(),
                "issued",
                dte.issuedAt());
    }
}