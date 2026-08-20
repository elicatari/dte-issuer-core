package com.elicatari.dteissuer.shared;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * MDC de la petición: {@code tenant_id} y correlación. El dominio no lo conoce;
 * el filtro lo llena y lo vacía al terminar, también si la petición falla.
 */
public final class RequestMdc {

    public static final String HEADER = "X-Request-Id";
    public static final String TENANT_ID = "tenant_id";
    public static final String REQUEST_ID = "request_id";

    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._\\-]{1,128}");

    private RequestMdc() {}

    /**
     * Usa el header si es corto y seguro; si no, genera uno. Evita saltos de
     * línea u otros caracteres que contaminen el log.
     */
    public static String resolve(String incoming) {
        if (incoming == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = incoming.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH || !SAFE.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }

    public static void putRequestId(String requestId) {
        MDC.put(REQUEST_ID, requestId);
    }

    public static void putTenantId(String tenantId) {
        MDC.put(TENANT_ID, tenantId);
    }

    public static void clear() {
        MDC.clear();
    }
}