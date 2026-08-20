package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.MissingTenantException;
import com.elicatari.dteissuer.shared.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = PersistenceSliceTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JpaDteRepositoryTest extends AbstractJpaPostgresTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final TenantId BETA = new TenantId("beta");
    private static final Rut RUT = Rut.parse("12.345.678-5");

    @Autowired
    private DteRepository dtes;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void findByIdOfAnotherTenantReturnsEmpty() {
        TenantContext.set(ALPHA);
        Dte alpha = dtes.save(Dte.issue(ALPHA, new Folio(90), RUT, Money.pesos(1000), Instant.parse("2026-08-18T20:00:00Z")));

        TenantContext.set(BETA);
        assertThat(dtes.findById(BETA, alpha.id())).isEmpty();

        TenantContext.set(ALPHA);
        assertThat(dtes.findById(ALPHA, alpha.id())).map(Dte::id).contains(alpha.id());
    }

    @Test
    void findByTenantIdDoesNotIncludeOtherTenant() {
        TenantContext.set(ALPHA);
        Dte alpha = dtes.save(Dte.issue(ALPHA, new Folio(93), RUT, Money.pesos(1000), Instant.parse("2026-08-18T20:00:00Z")));
        TenantContext.set(BETA);
        Dte beta = dtes.save(Dte.issue(BETA, new Folio(1090), RUT, Money.pesos(1000), Instant.parse("2026-08-18T20:00:00Z")));

        TenantContext.set(ALPHA);
        assertThat(dtes.findByTenantId(ALPHA))
                .extracting(Dte::id)
                .contains(alpha.id())
                .doesNotContain(beta.id());
        TenantContext.set(BETA);
        assertThat(dtes.findByTenantId(BETA))
                .extracting(Dte::id)
                .contains(beta.id())
                .doesNotContain(alpha.id());
    }

    @Test
    void nativeQueryWithoutTenantWhereDoesNotReturnOtherTenant() {
        TenantContext.set(ALPHA);
        Dte alpha = dtes.save(Dte.issue(ALPHA, new Folio(91), RUT, Money.pesos(1000), Instant.parse("2026-08-18T20:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        TenantContext.set(BETA);
        TenantSession.bind(entityManager, BETA);

        DteEntity viaFind = entityManager.find(DteEntity.class, alpha.id());
        assertThat(viaFind).isNull();

        List<?> nativeIds = entityManager.createNativeQuery("select id from dtes").getResultList();
        assertThat(nativeIds.stream().map(Object::toString)).doesNotContain(alpha.id().toString());

        List<DteEntity> viaQuery = entityManager
                .createQuery("select d from DteEntity d", DteEntity.class)
                .getResultList();
        assertThat(viaQuery).extracting(DteEntity::id).doesNotContain(alpha.id());

        assertThat(dtes.findById(BETA, alpha.id())).isEmpty();
    }

    @Test
    void sessionWithoutTenantFailsClosed() {
        TenantContext.clear();
        assertThatThrownBy(() -> dtes.findById(ALPHA, java.util.UUID.randomUUID()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(MissingTenantException.class);
    }
}