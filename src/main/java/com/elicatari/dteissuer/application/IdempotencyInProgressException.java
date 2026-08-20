package com.elicatari.dteissuer.application;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.domain.TenantId;

/**
 * Otro request con la misma clave aún no terminó. HTTP 409 en F2-T06; el cliente reintenta
 * y obtiene el DTE original, no un segundo folio.
 */
public class IdempotencyInProgressException extends ApplicationException {

    private final TenantId tenantId;
    private final IdempotencyKey key;

    public IdempotencyInProgressException(TenantId tenantId, IdempotencyKey key) {
        super("Idempotency-Key en curso para el tenant " + tenantId.value());
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