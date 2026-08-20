package com.elicatari.dteissuer.adapter.out.messaging;

import com.elicatari.dteissuer.adapter.out.persistence.JpaOutboxStore;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Un muerto no tumba el servicio: el indicador se degrada en detalles, el
 * agregado sigue {@code UP} para no fallar readiness.
 */
@Component
class OutboxHealthIndicator implements HealthIndicator {

    private final JpaOutboxStore outboxStore;

    OutboxHealthIndicator(JpaOutboxStore outboxStore) {
        this.outboxStore = outboxStore;
    }

    @Override
    public Health health() {
        try {
            long dead = outboxStore.deadLetteredCount();
            Health.Builder builder = Health.up().withDetail("deadLettered", dead);
            if (dead > 0) {
                builder.withDetail("alert", "degraded");
            }
            return builder.build();
        } catch (RuntimeException ex) {
            return Health.up().withDetail("error", ex.toString()).build();
        }
    }
}