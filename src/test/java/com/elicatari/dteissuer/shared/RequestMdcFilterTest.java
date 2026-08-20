package com.elicatari.dteissuer.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.adapter.in.DteController;
import com.elicatari.dteissuer.adapter.in.HttpJwtTestConfig;
import com.elicatari.dteissuer.adapter.out.persistence.TransactionalIssueDteUseCase;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = DteController.class)
@Import({
    SecurityConfig.class,
    TenantContextFilter.class,
    RequestMdcFilter.class,
    ApiExceptionHandler.class,
    HttpJwtTestConfig.class
})
class RequestMdcFilterTest {

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
    void unauthorizedStillReturnsCorrelationHeaderAndClearsMdc() throws Exception {
        mockMvc.perform(get("/api/v1/dte"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestMdc.HEADER));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void incomingRequestIdIsEchoed() throws Exception {
        when(dtes.findByTenantId(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header(RequestMdc.HEADER, "corr-alpha-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestMdc.HEADER, "corr-alpha-1"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        when(dtes.findByTenantId(any())).thenReturn(List.of());
        MvcResult result = mockMvc.perform(get("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header(RequestMdc.HEADER, "bad\nid"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getHeader(RequestMdc.HEADER)).isNotEqualTo("bad\nid");
    }

    @Test
    void authenticatedRequestPutsTenantAndRequestIdInMdcDuringHandler() throws Exception {
        when(dtes.findByTenantId(any())).thenAnswer(invocation -> {
            assertThat(MDC.get(RequestMdc.TENANT_ID)).isEqualTo("alpha");
            assertThat(MDC.get(RequestMdc.REQUEST_ID)).isEqualTo("corr-in-handler");
            return List.of();
        });
        mockMvc.perform(get("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header(RequestMdc.HEADER, "corr-in-handler"))
                .andExpect(status().isOk());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void failedRequestStillClearsMdc() throws Exception {
        mockMvc.perform(get("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .header("X-Tenant-Id", "beta"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(RequestMdc.HEADER));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}