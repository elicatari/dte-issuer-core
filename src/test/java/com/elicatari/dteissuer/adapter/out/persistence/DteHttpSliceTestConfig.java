package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.adapter.in.DteController;
import com.elicatari.dteissuer.adapter.in.HttpJwtTestConfig;
import com.elicatari.dteissuer.shared.ApiExceptionHandler;
import com.elicatari.dteissuer.shared.RequestMdcFilter;
import com.elicatari.dteissuer.shared.SecurityConfig;
import com.elicatari.dteissuer.shared.TenantContextFilter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {RabbitAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@Import({
    PersistenceJpaConfig.class,
    JpaDteRepository.class,
    JpaFolioRangeRepository.class,
    JpaIdempotencyStore.class,
    SpringDomainEventPublisher.class,
    TransactionalIssueDteUseCase.class,
    SecurityConfig.class,
    TenantContextFilter.class,
    RequestMdcFilter.class,
    ApiExceptionHandler.class,
    DteController.class,
    HttpJwtTestConfig.class
})
class DteHttpSliceTestConfig {}