package com.elicatari.dteissuer.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de outbox lista para publicar. El adapter AMQP no ve la entidad JPA.
 */
public record OutboxRecord(
        UUID id,
        String tenantId,
        String eventName,
        String eventVersion,
        String payload,
        Instant occurredAt) {}