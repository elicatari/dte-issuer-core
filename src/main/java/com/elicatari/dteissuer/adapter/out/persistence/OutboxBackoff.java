package com.elicatari.dteissuer.adapter.out.persistence;

import java.time.Instant;

/**
 * Backoff exponencial con tope. {@code attempts} es el recuento ya incrementado.
 */
final class OutboxBackoff {

    private OutboxBackoff() {}

    static Instant nextAttempt(Instant now, int attempts, long initialBackoffMs, long maxBackoffMs) {
        if (initialBackoffMs <= 0 || maxBackoffMs <= 0) {
            return now;
        }
        int shift = Math.min(Math.max(attempts, 1) - 1, 16);
        long delay = initialBackoffMs * (1L << shift);
        if (delay < 0 || delay > maxBackoffMs) {
            delay = maxBackoffMs;
        }
        return now.plusMillis(delay);
    }
}