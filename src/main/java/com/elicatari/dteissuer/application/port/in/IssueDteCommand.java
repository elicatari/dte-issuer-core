package com.elicatari.dteissuer.application.port.in;

import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.Objects;

/**
 * Pedido de emisión de Boleta 39. {@code tenantId} lo pone el adapter desde el JWT,
 * nunca desde un header de tenant.
 */
public record IssueDteCommand(TenantId tenantId, IdempotencyKey idempotencyKey, Rut rut, Money neto) {

    public IssueDteCommand {
        Objects.requireNonNull(tenantId, "tenant_id es obligatorio");
        Objects.requireNonNull(idempotencyKey, "Idempotency-Key es obligatorio");
        Objects.requireNonNull(rut, "el RUT es obligatorio");
        Objects.requireNonNull(neto, "el neto es obligatorio");
        if (!neto.isPositive()) {
            throw new IllegalArgumentException("el neto debe ser mayor a 0");
        }
    }
}