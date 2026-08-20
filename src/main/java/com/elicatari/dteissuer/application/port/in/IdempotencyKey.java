package com.elicatari.dteissuer.application.port.in;

/**
 * Clave de idempotencia HTTP. El scope real es {@code (tenant_id, key)}; esta clase
 * solo valida el valor que viaja en {@code Idempotency-Key}.
 */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 255;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key es obligatorio");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key supera " + MAX_LENGTH + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}