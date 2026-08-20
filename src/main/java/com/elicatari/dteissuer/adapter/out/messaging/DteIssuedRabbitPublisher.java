package com.elicatari.dteissuer.adapter.out.messaging;

import com.elicatari.dteissuer.adapter.out.persistence.JpaOutboxStore;
import com.elicatari.dteissuer.adapter.out.persistence.OutboxRecord;
import com.elicatari.dteissuer.domain.DteIssued;
import com.elicatari.dteissuer.shared.LogRedaction;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica filas de outbox a {@code dte.issued}. El use case no conoce AMQP.
 * Si Rabbit falla, la fila queda pendiente y el poller reintenta.
 *
 * <p>El listener {@code AFTER_COMMIT} publica con el payload del evento (sin
 * reabrir JPA en {@code afterCompletion}) y marca el outbox por JDBC. El poller
 * sí usa transacción JPA.
 */
@Component
public class DteIssuedRabbitPublisher {

    private static final Logger log = LoggerFactory.getLogger(DteIssuedRabbitPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final JpaOutboxStore outboxStore;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration pollerGrace;
    private final int pollerBatchSize;

    DteIssuedRabbitPublisher(
            RabbitTemplate rabbitTemplate,
            JpaOutboxStore outboxStore,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            Clock clock,
            OutboxPollerProperties pollerProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.pollerGrace = Duration.ofMillis(pollerProperties.graceMs());
        this.pollerBatchSize = pollerProperties.batchSize();
    }

    /**
     * Tras commit: publica con el hecho ya conocido. No reclama por JPA aquí —
     * {@code afterCompletion} no enlaza bien un EntityManager nuevo.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onIssued(DteIssued event) {
        DteIssuedMessage payload = DteIssuedMessage.from(event);
        try {
            send(payload, event.eventId(), DteIssuedQueues.EVENT_NAME, DteIssuedQueues.EVENT_VERSION);
            outboxStore.markPublishedDirect(event.eventId(), clock.instant());
            logPublished(payload);
        } catch (RuntimeException ex) {
            log.error(
                    "DteIssued queda en outbox para reintento eventId={} tenant_id={}: {}",
                    event.eventId(),
                    event.tenantId().value(),
                    ex.toString());
        }
    }

    /**
     * Reintenta pendientes. In-process: no es un segundo bounded context.
     */
    public void publishUnpublished() {
        transactionTemplate.executeWithoutResult(status -> {
            for (OutboxRecord row :
                    outboxStore.claimUnpublishedBatch(clock.instant().minus(pollerGrace), pollerBatchSize)) {
                publishRow(row);
            }
        });
    }

    private void publishRow(OutboxRecord row) {
        try {
            DteIssuedMessage payload = objectMapper.readValue(row.payload(), DteIssuedMessage.class);
            send(payload, row.id(), row.eventName(), row.eventVersion());
            outboxStore.markPublished(row.id(), clock.instant());
            logPublished(payload);
        } catch (RuntimeException ex) {
            log.error(
                    "DteIssued queda en outbox para reintento eventId={} tenant_id={}: {}",
                    row.id(),
                    row.tenantId(),
                    ex.toString());
        }
    }

    private void send(DteIssuedMessage payload, UUID eventId, String eventName, String eventVersion) {
        rabbitTemplate.convertAndSend(DteIssuedQueues.NAME, payload, message -> {
            message.getMessageProperties().setHeader(DteIssuedQueues.HEADER_EVENT_NAME, eventName);
            message.getMessageProperties().setHeader(DteIssuedQueues.HEADER_EVENT_VERSION, eventVersion);
            message.getMessageProperties().setMessageId(eventId.toString());
            return message;
        });
    }

    private static void logPublished(DteIssuedMessage payload) {
        log.info(
                "dte issued published queue={} dteId={} folio={} eventId={} rut={}",
                DteIssuedQueues.NAME,
                payload.dteId(),
                payload.folio(),
                payload.eventId(),
                LogRedaction.maskRut(payload.rut()));
    }
}