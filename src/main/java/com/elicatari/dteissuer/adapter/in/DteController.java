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
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DteController {

    private static final Logger log = LoggerFactory.getLogger(DteController.class);

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransactionalIssueDteUseCase issueDte;
    private final DteRepository dtes;

    DteController(TransactionalIssueDteUseCase issueDte, DteRepository dtes) {
        this.issueDte = issueDte;
        this.dtes = dtes;
    }

    @PostMapping
    ResponseEntity<DteResponse> issue(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
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
    ResponseEntity<DteResponse> get(@PathVariable UUID id) {
        return dtes.findById(TenantContext.require(), id)
                .map(DteResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    List<DteResponse> list() {
        return dtes.findByTenantId(TenantContext.require()).stream().map(DteResponse::from).toList();
    }
}