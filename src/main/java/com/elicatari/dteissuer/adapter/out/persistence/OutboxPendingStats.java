package com.elicatari.dteissuer.adapter.out.persistence;

import java.time.Instant;

/**
 * Pendientes del outbox con el mismo predicado que el relay
 * ({@code published_at is null} y {@code occurred_at} anterior al cutoff).
 */
public record OutboxPendingStats(long count, Instant oldestOccurredAt) {

    public static OutboxPendingStats empty() {
        return new OutboxPendingStats(0, null);
    }
}