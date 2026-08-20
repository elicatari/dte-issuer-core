package com.elicatari.dteissuer.application.port.out;

import com.elicatari.dteissuer.domain.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de {@code (tenant_id, idempotency_key)}: hash del request y DTE resultante.
 * {@code dteId} nulo significa que la emisión está en curso.
 */
public record IdempotencyRecord(TenantId tenantId, String key, String requestHash, UUID dteId) {

    public IdempotencyRecord {
        Objects.requireNonNull(tenantId, "tenant_id es obligatorio");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("la clave de idempotencia es obligatoria");
        }
        Objects.requireNonNull(requestHash, "el hash del request es obligatorio");
    }

    public static IdempotencyRecord started(TenantId tenantId, String key, String requestHash) {
        return new IdempotencyRecord(tenantId, key, requestHash, null);
    }

    public IdempotencyRecord completed(UUID issuedDteId) {
        Objects.requireNonNull(issuedDteId, "dteId es obligatorio al completar");
        return new IdempotencyRecord(tenantId, key, requestHash, issuedDteId);
    }

    public boolean inProgress() {
        return dteId == null;
    }

    public boolean isCompleted() {
        return dteId != null;
    }
}