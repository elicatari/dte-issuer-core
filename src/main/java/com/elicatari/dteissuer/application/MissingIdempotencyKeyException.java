package com.elicatari.dteissuer.application;

/**
 * POST de emisión sin header {@code Idempotency-Key}. HTTP 400 en F2-T06.
 */
public class MissingIdempotencyKeyException extends ApplicationException {

    public MissingIdempotencyKeyException() {
        super("Idempotency-Key es obligatorio");
    }
}