package com.elicatari.dteissuer.adapter.out.messaging;

import com.elicatari.dteissuer.adapter.out.persistence.JpaOutboxStore;
import com.elicatari.dteissuer.domain.DteIssued;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Persiste {@link DteIssued} en outbox en la misma transacción que el DTE.
 * No habla con Rabbit.
 */
@Component
public class OutboxWriter {

    private final JpaOutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    OutboxWriter(JpaOutboxStore outboxStore, ObjectMapper objectMapper) {
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
    }

    @EventListener
    void onIssued(DteIssued event) {
        try {
            String payload = objectMapper.writeValueAsString(DteIssuedMessage.from(event));
            outboxStore.append(
                    event, DteIssuedQueues.EVENT_NAME, DteIssuedQueues.EVENT_VERSION, payload);
        } catch (JacksonException ex) {
            throw new IllegalStateException("no se pudo serializar DteIssued para el outbox", ex);
        }
    }
}