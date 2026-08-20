package com.elicatari.dteissuer.adapter.out.messaging;

import com.elicatari.dteissuer.adapter.out.persistence.JpaOutboxStore;
import com.elicatari.dteissuer.adapter.out.persistence.OutboxPendingStats;
import com.elicatari.dteissuer.shared.DteMeters;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gauges del outbox. Misma consulta que el relay; RLS con {@code app.outbox_relay}
 * local a la TX, sin {@code BYPASSRLS}.
 */
@Component
class OutboxMetrics {

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

    private final JpaOutboxStore outboxStore;
    private final Clock clock;
    private final Duration grace;

    OutboxMetrics(
            MeterRegistry registry,
            JpaOutboxStore outboxStore,
            Clock clock,
            OutboxPollerProperties pollerProperties) {
        this.outboxStore = outboxStore;
        this.clock = clock;
        this.grace = Duration.ofMillis(pollerProperties.graceMs());
        Gauge.builder(DteMeters.OUTBOX_PENDING, this, OutboxMetrics::pendingCount)
                .description("Eventos pendientes de publicar (pasados el grace del poller)")
                .register(registry);
        Gauge.builder(DteMeters.OUTBOX_LAG, this, OutboxMetrics::lagSeconds)
                .description("Edad en segundos del pendiente más viejo")
                .register(registry);
    }

    private double pendingCount() {
        return stats().count();
    }

    private double lagSeconds() {
        OutboxPendingStats current = stats();
        if (current.oldestOccurredAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(current.oldestOccurredAt(), clock.instant()).toSeconds());
    }

    private OutboxPendingStats stats() {
        try {
            return outboxStore.unpublishedStats(clock.instant().minus(grace));
        } catch (RuntimeException ex) {
            log.warn("no se pudo leer outbox para métricas: {}", ex.toString());
            return OutboxPendingStats.empty();
        }
    }
}