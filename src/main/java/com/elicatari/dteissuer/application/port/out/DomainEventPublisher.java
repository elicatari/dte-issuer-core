package com.elicatari.dteissuer.application.port.out;

import com.elicatari.dteissuer.domain.DteIssued;

/**
 * Emisión del hecho de negocio. El adapter persiste outbox y publica a Rabbit
 * <em>después</em> del commit; este puerto no conoce AMQP.
 */
public interface DomainEventPublisher {

    void publish(DteIssued event);
}