package com.elicatari.dteissuer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de outbox. Sin {@code @Filter}: el poller debe ver todos los tenants
 * (RLS con {@code app.outbox_relay}); la petición sigue aislada por tenant.
 */
@Entity
@Table(name = "outbox")
class OutboxEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "event_name", nullable = false, updatable = false)
    private String eventName;

    @Column(name = "event_version", nullable = false, updatable = false)
    private String eventVersion;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEntity() {}

    OutboxEntity(
            UUID id,
            String tenantId,
            String eventName,
            String eventVersion,
            String payload,
            Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventName = eventName;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    UUID id() {
        return id;
    }

    String tenantId() {
        return tenantId;
    }

    String eventName() {
        return eventName;
    }

    String eventVersion() {
        return eventVersion;
    }

    String payload() {
        return payload;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    Instant publishedAt() {
        return publishedAt;
    }

    void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}