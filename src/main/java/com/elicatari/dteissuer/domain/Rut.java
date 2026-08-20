package com.elicatari.dteissuer.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RUT chileno con dígito verificador. Forma canónica: {@code 12345678-5} (sin puntos, K mayúscula).
 */
public record Rut(String value) {

    private static final Pattern CANONICAL = Pattern.compile("^(\\d{1,8})-([0-9K])$");

    public Rut {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("el RUT es obligatorio");
        }
        value = canonicalize(value);
        if (!hasValidCheckDigit(value)) {
            throw new IllegalArgumentException("RUT inválido: " + value);
        }
    }

    public static Rut parse(String raw) {
        return new Rut(raw);
    }

    public String body() {
        return value.substring(0, value.indexOf('-'));
    }

    public char checkDigit() {
        return value.charAt(value.length() - 1);
    }

    static String canonicalize(String raw) {
        String compact = raw.strip().replace(".", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!compact.contains("-") && compact.length() >= 2) {
            compact = compact.substring(0, compact.length() - 1) + "-" + compact.charAt(compact.length() - 1);
        }
        Matcher matcher = CANONICAL.matcher(compact);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("RUT con formato inválido: " + raw);
        }
        String body = matcher.group(1).replaceFirst("^0+", "");
        if (body.isEmpty()) {
            throw new IllegalArgumentException("RUT con formato inválido: " + raw);
        }
        return body + "-" + matcher.group(2);
    }

    static boolean hasValidCheckDigit(String canonical) {
        int dash = canonical.indexOf('-');
        String body = canonical.substring(0, dash);
        if (body.isEmpty()) {
            return false;
        }
        return checkDigitOf(body) == canonical.charAt(canonical.length() - 1);
    }

    static char checkDigitOf(String body) {
        int sum = 0;
        int multiplier = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            sum += Character.getNumericValue(body.charAt(i)) * multiplier;
            multiplier = multiplier == 7 ? 2 : multiplier + 1;
        }
        int remainder = 11 - (sum % 11);
        if (remainder == 11) {
            return '0';
        }
        if (remainder == 10) {
            return 'K';
        }
        return (char) ('0' + remainder);
    }
}