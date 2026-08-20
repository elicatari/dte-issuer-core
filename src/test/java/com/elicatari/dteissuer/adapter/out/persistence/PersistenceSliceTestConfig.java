package com.elicatari.dteissuer.adapter.out.persistence;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Slice de persistencia: sin Security ni Rabbit. El scan queda en este paquete.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            RabbitAutoConfiguration.class
        })
@Import({
    PersistenceJpaConfig.class,
    JpaDteRepository.class,
    JpaFolioRangeRepository.class,
    JpaIdempotencyStore.class,
    SpringDomainEventPublisher.class,
    TransactionalIssueDteUseCase.class
})
class PersistenceSliceTestConfig {}