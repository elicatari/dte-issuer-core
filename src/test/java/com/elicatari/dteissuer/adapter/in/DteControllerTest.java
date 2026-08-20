package com.elicatari.dteissuer.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.adapter.out.persistence.TransactionalIssueDteUseCase;
import com.elicatari.dteissuer.application.IdempotencyConflictException;
import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.NoFolioAvailableException;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.ApiExceptionHandler;
import com.elicatari.dteissuer.shared.LogRedaction;
import com.elicatari.dteissuer.shared.ProblemTypes;
import com.elicatari.dteissuer.shared.RequestMdc;
import com.elicatari.dteissuer.shared.RequestMdcFilter;
import com.elicatari.dteissuer.shared.SecurityConfig;
import com.elicatari.dteissuer.shared.TenantContext;
import com.elicatari.dteissuer.shared.TenantContextFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
class DteControllerTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final TenantId BETA = new TenantId("beta");
    private static final String BODY = "{\"rut\":\"12.345.678-5\",\"neto\":1000}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionalIssueDteUseCase issueDte;

    @MockitoBean
    private DteRepository dtes;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        RequestMdc.clear();
    }

    @Test
    void postWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/dte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1")
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutIdempotencyKeyIs400WithStableType() throws Exception {
        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ProblemTypes.MISSING_IDEMPOTENCY_KEY.toString()));
    }

    @Test
    void postIssuesBoleta39() throws Exception {
        Dte dte = sample(ALPHA, 1);
        when(issueDte.execute(any())).thenReturn(dte);

        Logger logger = (Logger) LoggerFactory.getLogger(DteController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/v1/dte")
                            .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "secret-idem-key")
                            .header(RequestMdc.HEADER, "corr-issue-1")
                            .content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/dte/" + dte.id()))
                    .andExpect(header().string(RequestMdc.HEADER, "corr-issue-1"))
                    .andExpect(jsonPath("$.folio").value(1))
                    .andExpect(jsonPath("$.status").value("issued"))
                    .andExpect(jsonPath("$.iva").value(190))
                    .andExpect(jsonPath("$.total").value(1190));

            assertThat(appender.list).isNotEmpty();
            ILoggingEvent event = appender.list.getFirst();
            String message = event.getFormattedMessage();
            assertThat(message).contains("dteId=" + dte.id());
            assertThat(message).contains("folio=1");
            assertThat(message).contains("result=issued");
            assertThat(message).contains(LogRedaction.maskRut(dte.rut().value()));
            assertThat(message).doesNotContain("12345678-5");
            assertThat(message).doesNotContain("12.345.678-5");
            assertThat(message).doesNotContain("secret-idem-key");
            assertThat(message).doesNotContain("Bearer");
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestMdc.TENANT_ID, "alpha");
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestMdc.REQUEST_ID, "corr-issue-1");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void postWithoutFolioIs409FolioExhaustedNotIdempotencyType() throws Exception {
        when(issueDte.execute(any())).thenThrow(new NoFolioAvailableException(ALPHA));

        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1")
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(ProblemTypes.FOLIO_EXHAUSTED.toString()));
    }

    @Test
    void postSameKeyDifferentBodyIs409ConflictDistinctType() throws Exception {
        when(issueDte.execute(any()))
                .thenThrow(new IdempotencyConflictException(ALPHA, new IdempotencyKey("k1")));

        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1")
                        .content("{\"rut\":\"12.345.678-5\",\"neto\":2000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(ProblemTypes.IDEMPOTENCY_CONFLICT.toString()));
    }

    @Test
    void getOtherTenantIs404Not403() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        when(dtes.findById(BETA, id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/dte/" + id).with(jwt().jwt(jwt -> jwt.claim("tenant_id", "beta"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDoesNotIncludeOtherTenant() throws Exception {
        when(dtes.findByTenantId(ALPHA)).thenReturn(List.of(sample(ALPHA, 3)));

        mockMvc.perform(get("/api/v1/dte").with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].folio").value(3))
                .andExpect(jsonPath("$[0].tenantId").value("alpha"));
    }

    private static Dte sample(TenantId tenantId, long folio) {
        return Dte.issue(
                tenantId,
                new Folio(folio),
                Rut.parse("12.345.678-5"),
                Money.pesos(1000),
                Instant.parse("2026-08-18T20:00:00Z"));
    }
}