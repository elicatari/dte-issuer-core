package com.elicatari.dteissuer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RutTest {

    @Test
    void canonicalizesDotsAndLowercaseK() {
        Rut rut = Rut.parse("12.345.678-5");
        assertThat(rut.value()).isEqualTo("12345678-5");
        assertThat(Rut.parse("12345678-5")).isEqualTo(rut);
    }

    @Test
    void acceptsCheckDigitK() {
        char digit = Rut.checkDigitOf("1234567");
        Rut rut = Rut.parse("1234567-" + digit);
        assertThat(rut.checkDigit()).isEqualTo(digit);
    }

    @Test
    void rejectsWrongCheckDigit() {
        assertThatThrownBy(() -> Rut.parse("12345678-9")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> Rut.parse("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knownOneDigitRut() {
        assertThat(Rut.parse("1-9").value()).isEqualTo("1-9");
    }
}