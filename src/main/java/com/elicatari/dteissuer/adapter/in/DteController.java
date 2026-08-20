package com.elicatari.dteissuer.adapter.in;

import com.elicatari.dteissuer.adapter.out.persistence.TransactionalIssueDteUseCase;
import com.elicatari.dteissuer.application.MissingIdempotencyKeyException;
import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.LogRedaction;
import com.elicatari.dteissuer.shared.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dte")
@Tag(name = "DTE", description = "Emisión de Boleta 39. El tenant sale del JWT.")
public class DteController {

    private static final Logger log = LoggerFactory.getLogger(DteController.class);

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final String PROBLEM_JSON = "application/problem+json";

    private final TransactionalIssueDteUseCase issueDte;
    private final DteRepository dtes;

    DteController(TransactionalIssueDteUseCase issueDte, DteRepository dtes) {
        this.issueDte = issueDte;
        this.dtes = dtes;
    }

    @PostMapping
    @Operation(summary = "Emitir Boleta 39")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "DTE emitido. Un folio. Replay con la misma key y body devuelve el mismo DTE.",
                headers = @Header(name = "Location", description = "URI del DTE"),
                content = @Content(schema = @Schema(implementation = DteResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Idempotency-Key ausente o body inválido.",
                content =
                        @Content(
                                mediaType = PROBLEM_JSON,
                                schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o inválido."),
        @ApiResponse(
                responseCode = "409",
                description =
                        "Folio agotado, Idempotency-Key en conflicto (mismo key, body distinto) o key en curso.",
                content =
                        @Content(
                                mediaType = PROBLEM_JSON,
                                schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    ResponseEntity<DteResponse> issue(
            @Parameter(
                            name = IDEMPOTENCY_KEY,
                            description = "Scope (tenant_id, key). Obligatorio. Mismo key con body distinto es 409.",
                            required = true,
                            example = "demo-1")
                    @RequestHeader(value = IDEMPOTENCY_KEY, required = false)
                    String idempotencyKey,
            @RequestBody IssueDteRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        if (request == null || request.rut() == null || request.neto() == null) {
            throw new IllegalArgumentException("rut y neto son obligatorios");
        }
        TenantId tenantId = TenantContext.require();
        Dte dte = issueDte.execute(new IssueDteCommand(
                tenantId, new IdempotencyKey(idempotencyKey), Rut.parse(request.rut()), Money.pesos(request.neto())));
        log.info(
                "dte issued dteId={} folio={} result=issued rut={} idempotencyKey={}",
                dte.id(),
                dte.folio().value(),
                LogRedaction.maskRut(dte.rut().value()),
                LogRedaction.hashSecret(idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/dte/" + dte.id())).body(DteResponse.from(dte));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un DTE del tenant autenticado")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                content = @Content(schema = @Schema(implementation = DteResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o inválido."),
        @ApiResponse(responseCode = "404", description = "No existe o pertenece a otro tenant.")
    })
    ResponseEntity<DteResponse> get(@PathVariable UUID id) {
        return dtes.findById(TenantContext.require(), id)
                .map(DteResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Listar DTE del tenant autenticado")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                array = @ArraySchema(schema = @Schema(implementation = DteResponse.class)))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o inválido.")
    })
    List<DteResponse> list() {
        return dtes.findByTenantId(TenantContext.require()).stream().map(DteResponse::from).toList();
    }
}