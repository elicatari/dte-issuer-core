package com.elicatari.dteissuer.domain;

/**
 * Identidad del tenant. En runtime llega del claim JWT, nunca de un header.
 */
public record TenantId(String value) {

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenant_id es obligatorio");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }
}