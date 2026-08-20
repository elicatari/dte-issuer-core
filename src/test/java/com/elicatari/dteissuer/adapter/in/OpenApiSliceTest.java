package com.elicatari.dteissuer.adapter.in;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.adapter.out.persistence.TransactionalIssueDteUseCase;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.shared.ApiExceptionHandler;
import com.elicatari.dteissuer.shared.RequestMdcFilter;
import com.elicatari.dteissuer.shared.SecurityConfig;
import com.elicatari.dteissuer.shared.TenantContextFilter;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato se genera. Sin JWT, 401: docs no se abren por comodidad de demo.
 */
@WebMvcTest(controllers = DteController.class)
@Import({
    SecurityConfig.class,
    TenantContextFilter.class,
    RequestMdcFilter.class,
    ApiExceptionHandler.class,
    HttpJwtTestConfig.class,
    OpenApiConfig.class
})
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class,
    SwaggerUiConfigProperties.class,
    SwaggerUiOAuthProperties.class,
    SwaggerConfig.class
})
class OpenApiSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionalIssueDteUseCase issueDte;

    @MockitoBean
    private DteRepository dtes;

    @Test
    void apiDocsWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsDescribesPostDteWithFourResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/dte'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dte'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dte'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dte'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dte'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dte'].post.parameters[?(@.name=='Idempotency-Key')].in")
                        .value("header"))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists());
    }

    @Test
    void swaggerUiWithJwtResponds() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha"))))
                .andExpect(status().isFound());
    }
}