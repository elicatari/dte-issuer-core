package com.elicatari.dteissuer.domain;

/**
 * Resultado de {@link FolioRange#reserveNext()}: el folio asignado y el rango ya avanzado.
 * El descuento de folio es este efecto, no el evento {@link DteIssued}.
 */
public record FolioReservation(Folio folio, FolioRange range) {

    public FolioReservation {
        if (folio == null || range == null) {
            throw new IllegalArgumentException("folio y rango son obligatorios");
        }
    }
}