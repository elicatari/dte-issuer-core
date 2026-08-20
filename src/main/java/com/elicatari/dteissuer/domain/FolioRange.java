package com.elicatari.dteissuer.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * CAF simulado: rango de folios por tenant. El cursor vive en el agregado, no en {@code MAX(folio)}.
 * La reserva es una sola operación. La concurrencia la serializa el adapter (F2-T04).
 */
public record FolioRange(UUID id, TenantId tenantId, Folio from, Folio to, Folio next) {

    public FolioRange {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(tenantId, "tenant_id es obligatorio");
        Objects.requireNonNull(from, "from es obligatorio");
        Objects.requireNonNull(to, "to es obligatorio");
        Objects.requireNonNull(next, "next es obligatorio");
        if (from.value() > to.value()) {
            throw new IllegalArgumentException("el rango de folios está invertido");
        }
        if (next.value() < from.value() || next.value() > to.value() + 1) {
            throw new IllegalArgumentException("el cursor de folio está fuera del rango");
        }
    }

    public static FolioRange open(TenantId tenantId, Folio from, Folio to) {
        return new FolioRange(UUID.randomUUID(), tenantId, from, to, from);
    }

    public boolean exhausted() {
        return next.value() > to.value();
    }

    /**
     * Devuelve el siguiente folio y avanza el cursor, o falla. No lee un máximo externo.
     */
    public FolioReservation reserveNext() {
        if (exhausted()) {
            throw new NoFolioAvailableException(tenantId);
        }
        Folio assigned = next;
        Folio advanced = assigned.value() == to.value() ? new Folio(to.value() + 1) : assigned.next();
        FolioRange updated = new FolioRange(id, tenantId, from, to, advanced);
        return new FolioReservation(assigned, updated);
    }
}