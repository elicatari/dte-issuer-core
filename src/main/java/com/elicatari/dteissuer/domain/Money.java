package com.elicatari.dteissuer.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pesos chilenos: escala 0, redondeo {@link RoundingMode#HALF_UP}. Nunca {@code double}.
 */
public record Money(BigDecimal amount) {

    public static final int SCALE = 0;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal IVA_RATE = new BigDecimal("0.19");

    public Money {
        Objects.requireNonNull(amount, "el monto es obligatorio");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("el monto no puede ser negativo");
        }
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("el peso chileno no admite decimales: " + amount, ex);
        }
    }

    public static Money pesos(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return pesos(0);
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    /**
     * IVA u otro porcentaje, redondeado a peso entero con HALF_UP.
     */
    public Money percentage(BigDecimal rate) {
        Objects.requireNonNull(rate, "la tasa es obligatoria");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("la tasa no puede ser negativa");
        }
        BigDecimal rounded = amount.multiply(rate).setScale(SCALE, ROUNDING);
        return new Money(rounded);
    }

    public Money iva19() {
        return percentage(IVA_RATE);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }
}