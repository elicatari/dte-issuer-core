package com.elicatari.dteissuer.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.elicatari.dteissuer.domain.DocumentType;
import com.elicatari.dteissuer.domain.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DteMetersTest {

    @Test
    void issuedCounterIsPerTenantAndDoesNotUseFolioOrRut() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DteMeters meters = new DteMeters(registry);

        meters.recordIssued(new TenantId("alpha"), DocumentType.BOLETA_39);
        meters.recordIssued(new TenantId("alpha"), DocumentType.BOLETA_39);
        meters.recordIssued(new TenantId("beta"), DocumentType.BOLETA_39);

        assertThat(registry
                        .find(DteMeters.ISSUED)
                        .tag(DteMeters.TAG_TENANT_ID, "alpha")
                        .tag(DteMeters.TAG_DOCUMENT_TYPE, "BOLETA_39")
                        .counter()
                        .count())
                .isEqualTo(2);
        assertThat(registry
                        .find(DteMeters.ISSUED)
                        .tag(DteMeters.TAG_TENANT_ID, "beta")
                        .tag(DteMeters.TAG_DOCUMENT_TYPE, "BOLETA_39")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream()))
                .noneMatch(tag -> tag.getKey().contains("folio") || tag.getKey().contains("rut"));
    }
}