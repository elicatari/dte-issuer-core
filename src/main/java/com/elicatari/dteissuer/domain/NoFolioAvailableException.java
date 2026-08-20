package com.elicatari.dteissuer.domain;

/**
 * El CAF del tenant no tiene folio vigente: no se emite DTE.
 */
public class NoFolioAvailableException extends DomainException {

    private final TenantId tenantId;

    public NoFolioAvailableException(TenantId tenantId) {
        super("No hay folio vigente para el tenant " + tenantId.value());
        this.tenantId = tenantId;
    }

    public TenantId tenantId() {
        return tenantId;
    }
}