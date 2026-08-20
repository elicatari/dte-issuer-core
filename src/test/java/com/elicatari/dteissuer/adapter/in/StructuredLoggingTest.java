package com.elicatari.dteissuer.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.adapter.out.persistence.TransactionalIssueDteUseCase;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.ApiExceptionHandler;
import com.elicatari.dteissuer.shared.LogRedaction;
import com.elicatari.dteissuer.shared.RequestMdc;
import com.elicatari.dteissuer.shared.RequestMdcFilter;
import com.elicatari.dteissuer.shared.SecurityConfig;
import com.elicatari.dteissuer.shared.TenantContext;
import com.elicatari.dteissuer.shared.TenantContextFilter;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ECS en consola: MDC como campos JSON; RUT e Idempotency-Key siguen redactados.
 */
@WebMvcTest(controllers = DteController.class)
@Import({
    SecurityConfig.class,
    TenantContextFilter.class,
    RequestMdcFilter.class,
    ApiExceptionHandler.class,
    HttpJwtTestConfig.class
})
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    private static final String BODY = "{\"rut\":\"12.345.678-5\",\"neto\":1000}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    void issuedLogIsJsonWithMdcAndRedaction(CapturedOutput output) throws Exception {
        Dte dte = Dte.issue(
                new TenantId("alpha"),
                new Folio(1),
                Rut.parse("12.345.678-5"),
                Money.pesos(1000),
                Instant.parse("2026-08-18T20:00:00Z"));
        when(issueDte.execute(any())).thenReturn(dte);

        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "secret-idem-key")
                        .header(RequestMdc.HEADER, "corr-json-1")
                        .content(BODY))
                .andExpect(status().isCreated());

        JsonNode line = issuedJsonLine(output.getOut());
        assertThat(line.get("tenant_id").asString()).isEqualTo("alpha");
        assertThat(line.get("request_id").asString()).isEqualTo("corr-json-1");
        String raw = line.toString();
        assertThat(raw).contains(LogRedaction.maskRut(dte.rut().value()));
        assertThat(raw).doesNotContain("12345678-5");
        assertThat(raw).doesNotContain("12.345.678-5");
        assertThat(raw).doesNotContain("secret-idem-key");
    }

    private static JsonNode issuedJsonLine(String console) {
        return Arrays.stream(console.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("{") && line.contains("dte issued"))
                .map(line -> JSON.readTree(line))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hubo línea JSON de emisión:\n" + console));
    }
}