package com.elicatari.dteissuer.adapter.in;

import com.elicatari.dteissuer.shared.ProblemTypes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato HTTP generado. Las rutas de docs quedan autenticadas: no se abren
 * por comodidad de demo.
 */
@Configuration
class OpenApiConfig {

    static final String BEARER_JWT = "bearer-jwt";

    @Bean
    OpenAPI dteIssuerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DTE Issuer")
                        .version("v1")
                        .description(
                                """
                                Emisión de Boleta 39. El tenant sale del JWT (`tenant_id`), \
                                no de un header. `Idempotency-Key` es obligatorio. \
                                Errores en RFC 9457; `type` identifica la clase \
                                (`folio-exhausted`, `idempotency-conflict`, \
                                `idempotency-in-progress`, `missing-idempotency-key`, \
                                `invalid-request`).
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT de Keycloak. Claim `tenant_id` obligatorio."))
                        .addSchemas("ProblemDetail", problemDetailSchema()));
    }

    private static Schema<?> problemDetailSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.description("RFC 9457. `type` es una URI estable de ProblemTypes.");
        schema.addProperty(
                "type",
                new StringSchema()
                        .format("uri")
                        .description("Clase de error")
                        .example(ProblemTypes.FOLIO_EXHAUSTED.toString()));
        schema.addProperty("title", new StringSchema());
        schema.addProperty("status", new IntegerSchema().format("int32"));
        schema.addProperty("detail", new StringSchema());
        schema.addRequiredItem("type");
        schema.addRequiredItem("status");
        return schema;
    }
}