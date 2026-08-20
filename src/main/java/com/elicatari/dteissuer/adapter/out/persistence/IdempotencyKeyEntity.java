package com.elicatari.dteissuer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "idempotency_keys")
@Filter(name = TenantFilters.NAME, condition = TenantFilters.CONDITION)
class IdempotencyKeyEntity {

    @EmbeddedId
    private IdempotencyKeyPk pk;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "dte_id")
    private UUID dteId;

    protected IdempotencyKeyEntity() {}

    IdempotencyKeyPk pk() {
        return pk;
    }

    String requestHash() {
        return requestHash;
    }

    UUID dteId() {
        return dteId;
    }
}