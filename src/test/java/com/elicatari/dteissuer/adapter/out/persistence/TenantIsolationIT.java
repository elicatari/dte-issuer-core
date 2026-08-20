package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.TenantContext;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Alpha emite; Beta no ve esa fila por HTTP ni por repositorio. JWT con claim;
 * no levanta Keycloak. No es ArchUnit.
 */
@SpringBootTest(classes = DteHttpSliceTestConfig.class)
@AutoConfigureMockMvc
class TenantIsolationIT extends AbstractJpaPostgresTest {

    private static final String BODY = "{\"rut\":\"12.345.678-5\",\"neto\":1000}";
    private static final TenantId ALPHA = new TenantId("alpha");
    private static final TenantId BETA = new TenantId("beta");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DteRepository dtes;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void betaGetDoesNotSeeAlphaDte() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "it-isolation-alpha")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/dte/" + id).with(jwt().jwt(jwt -> jwt.claim("tenant_id", "beta"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/dte").with(jwt().jwt(jwt -> jwt.claim("tenant_id", "beta"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").doesNotExist());

        mockMvc.perform(get("/api/v1/dte/" + id).with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void repositoryFindByIdOfAnotherTenantIsEmpty() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "it-isolation-repo")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        TenantContext.set(BETA);
        assertThat(dtes.findById(BETA, id)).isEmpty();

        TenantContext.set(ALPHA);
        assertThat(dtes.findById(ALPHA, id)).map(dte -> dte.id()).contains(id);
    }

    @Test
    void alphaJwtCannotSwitchTenantWithHeader() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "it-isolation-header")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/dte/" + id)
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header("X-Tenant-Id", "beta"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/dte/" + id).with(jwt().jwt(jwt -> jwt.claim("tenant_id", "beta"))))
                .andExpect(status().isNotFound());
    }
}