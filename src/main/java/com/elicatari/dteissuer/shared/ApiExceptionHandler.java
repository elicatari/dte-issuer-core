package com.elicatari.dteissuer.shared;

import com.elicatari.dteissuer.application.IdempotencyConflictException;
import com.elicatari.dteissuer.application.IdempotencyInProgressException;
import com.elicatari.dteissuer.application.MissingIdempotencyKeyException;
import com.elicatari.dteissuer.domain.NoFolioAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de emisión en RFC 9457. Cada clase de error tiene su {@code type}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NoFolioAvailableException.class)
    ProblemDetail folioExhausted(NoFolioAvailableException ex) {
        log.info("dte issue failed result=folio_exhausted");
        return problem(HttpStatus.CONFLICT, ProblemTypes.FOLIO_EXHAUSTED, "Folio agotado", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException ex) {
        log.info(
                "dte issue failed result=idempotency_conflict idempotencyKey={}",
                LogRedaction.hashSecret(ex.key().value()));
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key en conflicto",
                ex.getMessage());
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    ProblemDetail idempotencyInProgress(IdempotencyInProgressException ex) {
        log.info(
                "dte issue failed result=idempotency_in_progress idempotencyKey={}",
                LogRedaction.hashSecret(ex.key().value()));
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.IDEMPOTENCY_IN_PROGRESS,
                "Idempotency-Key en curso",
                ex.getMessage());
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    ProblemDetail missingIdempotencyKey(MissingIdempotencyKeyException ex) {
        log.info("dte issue failed result=missing_idempotency_key");
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.MISSING_IDEMPOTENCY_KEY,
                "Idempotency-Key obligatorio",
                ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException ex) {
        log.info("dte issue failed result=invalid_request");
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.INVALID_REQUEST, "Solicitud inválida", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, java.net.URI type, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(type);
        body.setTitle(title);
        return body;
    }
}