package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.port.out.DomainEventPublisher;
import com.elicatari.dteissuer.domain.DteIssued;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica el hecho de dominio como evento de aplicación. El adapter escribe
 * outbox en la misma TX y publica a {@code dte.issued} tras el commit.
 */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DteIssued event) {
        publisher.publishEvent(event);
    }
}