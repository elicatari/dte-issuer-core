package com.elicatari.dteissuer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void rejectsFractionalPesos() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimales");
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> Money.pesos(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iva19UsesHalfUpToWholePesos() {
        assertThat(Money.pesos(1000).iva19()).isEqualTo(Money.pesos(190));
        assertThat(Money.pesos(1).iva19()).isEqualTo(Money.zero());
        assertThat(Money.pesos(3).iva19()).isEqualTo(Money.pesos(1));
    }

    @Test
    void plusAddsWithoutChangingScale() {
        assertThat(Money.pesos(10).plus(Money.pesos(5))).isEqualTo(Money.pesos(15));
    }

    @Test
    void rejectsNegativeRate() {
        assertThatThrownBy(() -> Money.pesos(100).percentage(new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}