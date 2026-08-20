package com.elicatari.dteissuer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class IdempotencyKeyPk implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "idempotency_key", nullable = false)
    private String key;

    protected IdempotencyKeyPk() {}

    IdempotencyKeyPk(String tenantId, String key) {
        this.tenantId = tenantId;
        this.key = key;
    }

    String tenantId() {
        return tenantId;
    }

    String key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyKeyPk that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, key);
    }
}