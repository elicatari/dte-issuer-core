package com.elicatari.dteissuer.adapter.out.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cola {@code dte.issued}. El outbox vive en la misma TX que el DTE; este
 * config solo declara la cola, el converter y el scheduling del poller (ADR 0002).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxPollerProperties.class)
public class RabbitConfig {

    @Bean
    Queue dteIssuedQueue() {
        return QueueBuilder.durable(DteIssuedQueues.NAME).build();
    }

    @Bean
    MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}