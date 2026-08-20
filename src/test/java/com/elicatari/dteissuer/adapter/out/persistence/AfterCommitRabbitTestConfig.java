package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.adapter.out.messaging.DteIssuedRabbitPublisher;
import com.elicatari.dteissuer.adapter.out.messaging.OutboxPollerProperties;
import com.elicatari.dteissuer.adapter.out.messaging.OutboxWriter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persistencia + outbox + listener AFTER_COMMIT con {@code RabbitTemplate} mock.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            RabbitAutoConfiguration.class
        })
@EnableConfigurationProperties(OutboxPollerProperties.class)
@Import({
    PersistenceJpaConfig.class,
    JpaDteRepository.class,
    JpaFolioRangeRepository.class,
    JpaIdempotencyStore.class,
    JpaOutboxStore.class,
    SpringDomainEventPublisher.class,
    TransactionalIssueDteUseCase.class,
    OutboxWriter.class,
    DteIssuedRabbitPublisher.class
})
class AfterCommitRabbitTestConfig {

    @Bean
    JsonMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}