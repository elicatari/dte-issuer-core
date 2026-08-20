package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El GUC se fija al abrir la TX desde {@link TenantContext}. Una nativa sin
 * {@code WHERE tenant_id} (el {@code @Filter} no aplica) no ve al otro tenant.
 */
@SpringBootTest(classes = PersistenceSliceTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RlsJpaIsolationTest extends AbstractJpaPostgresTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final TenantId BETA = new TenantId("beta");
    private static final Rut RUT = Rut.parse("12.345.678-5");

    @Autowired
    private DteRepository dtes;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void nativeQueryInBetaTransactionDoesNotSeeAlpha() {
        TenantContext.set(ALPHA);
        Dte alpha = transactionTemplate.execute(status -> dtes.save(
                Dte.issue(ALPHA, new Folio(92), RUT, Money.pesos(1000), Instant.parse("2026-08-18T20:00:00Z"))));
        assertThat(alpha).isNotNull();

        TenantContext.set(BETA);
        List<?> ids = transactionTemplate.execute(
                status -> entityManager.createNativeQuery("select id from dtes").getResultList());

        assertThat(ids.stream().map(Object::toString)).doesNotContain(alpha.id().toString());
    }
}