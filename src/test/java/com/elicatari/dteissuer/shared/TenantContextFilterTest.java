package com.elicatari.dteissuer.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.adapter.in.DteController;
import com.elicatari.dteissuer.adapter.in.HttpJwtTestConfig;
import com.elicatari.dteissuer.adapter.out.persistence.TransactionalIssueDteUseCase;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DteController.class)
@Import({
    SecurityConfig.class,
    TenantContextFilter.class,
    RequestMdcFilter.class,
    ApiExceptionHandler.class,
    HttpJwtTestConfig.class
})
class TenantContextFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionalIssueDteUseCase issueDte;

    @MockitoBean
    private DteRepository dtes;

    @AfterEach
    void clearThreadLocals() {
        TenantContext.clear();
        RequestMdc.clear();
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dte")).andExpect(status().isUnauthorized());
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void jwtWithoutTenantIdIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dte").with(jwt())).andExpect(status().isUnauthorized());
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void alphaJwtFillsContextFromClaim() throws Exception {
        when(dtes.findByTenantId(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/dte").with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void alphaJwtWithBetaHeaderDoesNotSwitchTenant() throws Exception {
        mockMvc.perform(get("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header("X-Tenant-Id", "beta"))
                .andExpect(status().isBadRequest());
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void tenantIdHeaderIsRejectedEvenWhenItMatchesTheJwt() throws Exception {
        mockMvc.perform(get("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header("tenant_id", "alpha"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantIdQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dte")
                        .param("tenant_id", "beta")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha"))))
                .andExpect(status().isBadRequest());
    }
}