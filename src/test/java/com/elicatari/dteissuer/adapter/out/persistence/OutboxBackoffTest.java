package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxBackoffTest {

    private static final Instant NOW = Instant.parse("2026-08-19T20:00:00Z");

    @Test
    void doublesUntilCapAndZeroMeansImmediate() {
        assertThat(OutboxBackoff.nextAttempt(NOW, 1, 2_000, 60_000)).isEqualTo(NOW.plusMillis(2_000));
        assertThat(OutboxBackoff.nextAttempt(NOW, 2, 2_000, 60_000)).isEqualTo(NOW.plusMillis(4_000));
        assertThat(OutboxBackoff.nextAttempt(NOW, 6, 2_000, 60_000)).isEqualTo(NOW.plusMillis(60_000));
        assertThat(OutboxBackoff.nextAttempt(NOW, 1, 0, 60_000)).isEqualTo(NOW);
    }
}