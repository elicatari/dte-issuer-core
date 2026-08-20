package com.elicatari.dteissuer.shared;

import java.net.URI;

/**
 * {@code type} estables de RFC 9457. Folio agotado y colisión de key no comparten tipo.
 */
public final class ProblemTypes {

    public static final URI FOLIO_EXHAUSTED = URI.create("https://elicatari.com/problems/folio-exhausted");
    public static final URI IDEMPOTENCY_CONFLICT =
            URI.create("https://elicatari.com/problems/idempotency-conflict");
    public static final URI IDEMPOTENCY_IN_PROGRESS =
            URI.create("https://elicatari.com/problems/idempotency-in-progress");
    public static final URI MISSING_IDEMPOTENCY_KEY =
            URI.create("https://elicatari.com/problems/missing-idempotency-key");
    public static final URI INVALID_REQUEST = URI.create("https://elicatari.com/problems/invalid-request");

    private ProblemTypes() {}
}