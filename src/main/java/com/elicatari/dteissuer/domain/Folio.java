package com.elicatari.dteissuer.domain;

/**
 * Número de folio SII. Positivo; la unicidad es ({@code tenant_id}, folio).
 */
public record Folio(long value) {

    public Folio {
        if (value < 1) {
            throw new IllegalArgumentException("el folio debe ser >= 1");
        }
    }

    public Folio next() {
        return new Folio(value + 1);
    }

    public boolean isAfter(Folio other) {
        return value > other.value;
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}