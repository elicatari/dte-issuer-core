package com.elicatari.dteissuer.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redacción para logs: RUT enmascarado, secretos hasheados. El token
 * {@code Authorization} no se loguea en ningún camino.
 */
public final class LogRedaction {

    private LogRedaction() {}

    /**
     * Conserva los dos últimos dígitos del cuerpo y el DV. {@code 12345678-5}
     * queda {@code ******78-5}.
     */
    public static String maskRut(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            return "***";
        }
        int dash = canonical.lastIndexOf('-');
        if (dash <= 0) {
            return "***";
        }
        String body = canonical.substring(0, dash);
        String suffix = canonical.substring(dash);
        if (body.length() <= 2) {
            return "*".repeat(body.length()) + suffix;
        }
        return "*".repeat(body.length() - 2) + body.substring(body.length() - 2) + suffix;
    }

    /** Primeros 12 hex de SHA-256. Sirve para {@code Idempotency-Key}. */
    public static String hashSecret(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}