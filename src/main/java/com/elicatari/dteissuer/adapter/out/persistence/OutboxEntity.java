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

    private static final int LAST_ERROR_MAX = 2_000;

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

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

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
        this.attempts = 0;
        this.nextAttemptAt = occurredAt;
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

    int attempts() {
        return attempts;
    }

    Instant deadLetteredAt() {
        return deadLetteredAt;
    }

    void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    /**
     * @return {@code true} si la fila queda muerta y el poller no la vuelve a tomar
     */
    boolean recordFailure(Instant now, String error, int maxAttempts, Instant nextAttempt) {
        this.attempts = this.attempts + 1;
        this.lastError = truncate(error);
        if (this.attempts >= maxAttempts) {
            this.deadLetteredAt = now;
            return true;
        }
        this.nextAttemptAt = nextAttempt;
        return false;
    }

    static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "unknown";
        }
        String trimmed = error.strip();
        if (trimmed.length() <= LAST_ERROR_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, LAST_ERROR_MAX);
    }
}