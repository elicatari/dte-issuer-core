package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.elicatari.dteissuer.adapter.out.messaging.DteIssuedQueues;
import com.elicatari.dteissuer.adapter.out.messaging.DteIssuedRabbitPublisher;
import com.elicatari.dteissuer.application.IssueDteUseCase;
import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = AfterCommitRabbitTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "dte.outbox.poller.grace-ms=0")
class DteIssuedAfterCommitTest extends AbstractJpaPostgresTest {

    private static final Rut RUT = Rut.parse("12.345.678-5");

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TransactionalIssueDteUseCase issueDte;

    @Autowired
    private IssueDteUseCase useCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DteIssuedRabbitPublisher publisher;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void commitPublishesToDteIssuedQueueAndMarksOutbox() throws SQLException {
        TenantId tenant = new TenantId("outbox-commit");
        insertFolioRange(dataSource, tenant.value(), 1, 10);
        TenantContext.set(tenant);
        issueDte.execute(command(tenant, "after-commit-ok"));

        verify(rabbitTemplate)
                .convertAndSend(eq(DteIssuedQueues.NAME), any(Object.class), any(MessagePostProcessor.class));
        assertThat(outboxCount(tenant.value())).isEqualTo(1);
        assertThat(unpublishedCount(tenant.value())).isZero();
    }

    @Test
    void rollbackDoesNotPublishNorLeaveOutbox() throws SQLException {
        TenantId tenant = new TenantId("outbox-rollback");
        insertFolioRange(dataSource, tenant.value(), 1, 10);
        TenantContext.set(tenant);
        transactionTemplate.executeWithoutResult((TransactionStatus status) -> {
            useCase.execute(command(tenant, "after-commit-rollback"));
            status.setRollbackOnly();
        });

        verify(rabbitTemplate, never())
                .convertAndSend(eq(DteIssuedQueues.NAME), any(Object.class), any(MessagePostProcessor.class));
        assertThat(outboxCount(tenant.value())).isZero();
    }

    @Test
    void rabbitFailureLeavesOutboxAndPollerPublishes() throws SQLException {
        TenantId tenant = new TenantId("outbox-retry");
        insertFolioRange(dataSource, tenant.value(), 1, 10);
        TenantContext.set(tenant);
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(eq(DteIssuedQueues.NAME), any(Object.class), any(MessagePostProcessor.class));

        issueDte.execute(command(tenant, "outbox-retry"));

        verify(rabbitTemplate)
                .convertAndSend(eq(DteIssuedQueues.NAME), any(Object.class), any(MessagePostProcessor.class));
        assertThat(unpublishedCount(tenant.value())).isEqualTo(1);

        reset(rabbitTemplate);
        publisher.publishUnpublished();

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(DteIssuedQueues.NAME), any(Object.class), any(MessagePostProcessor.class));
        assertThat(unpublishedCount(tenant.value())).isZero();
    }

    private long outboxCount(String tenantId) throws SQLException {
        return countOutbox(tenantId, "select count(*) from outbox");
    }

    private long unpublishedCount(String tenantId) throws SQLException {
        return countOutbox(tenantId, "select count(*) from outbox where published_at is null");
    }

    private long countOutbox(String tenantId, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            } finally {
                connection.commit();
            }
        }
    }

    private static IssueDteCommand command(TenantId tenantId, String key) {
        return new IssueDteCommand(tenantId, new IdempotencyKey(key), RUT, Money.pesos(1000));
    }
}