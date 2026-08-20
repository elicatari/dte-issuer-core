package com.elicatari.dteissuer.adapter.out.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Poller in-process: reintenta outbox pendiente. No es un worker ni un segundo
 * proceso.
 */
@Component
class OutboxPoller {

    private final DteIssuedRabbitPublisher publisher;

    OutboxPoller(DteIssuedRabbitPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${dte.outbox.poller.delay-ms:2000}")
    void tick() {
        publisher.publishUnpublished();
    }
}