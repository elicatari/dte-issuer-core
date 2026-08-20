package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.application.IssueDteUseCase;
import com.elicatari.dteissuer.application.port.out.DomainEventPublisher;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.application.port.out.FolioRangeRepository;
import com.elicatari.dteissuer.application.port.out.IdempotencyStore;
import com.elicatari.dteissuer.shared.DteMeters;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Import(DteMeters.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
class PersistenceJpaConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IssueDteUseCase issueDteUseCase(
            DteRepository dteRepository,
            FolioRangeRepository folioRangeRepository,
            IdempotencyStore idempotencyStore,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        return new IssueDteUseCase(
                dteRepository, folioRangeRepository, idempotencyStore, eventPublisher, clock);
    }

    @Bean
    TransactionExecutionListener tenantRlsTransactionListener(EntityManagerFactory entityManagerFactory) {
        return new TenantRlsTransactionListener(entityManagerFactory);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}