package com.elicatari.dteissuer.application;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.domain.TenantId;

/**
 * Mismo {@code Idempotency-Key} y tenant, body distinto. HTTP 409 en F2-T06.
 * No se devuelve el DTE original.
 */
public class IdempotencyConflictException extends ApplicationException {

    private final TenantId tenantId;
    private final IdempotencyKey key;

    public IdempotencyConflictException(TenantId tenantId, IdempotencyKey key) {
        super("Idempotency-Key reutilizado con un body distinto para el tenant " + tenantId.value());
        this.tenantId = tenantId;
        this.key = key;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public IdempotencyKey key() {
        return key;
    }
}