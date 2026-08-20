package com.elicatari.dteissuer.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogRedactionTest {

    @Test
    void maskRutHidesTheBodyKeepCheckDigit() {
        assertThat(LogRedaction.maskRut("12345678-5")).isEqualTo("******78-5");
        assertThat(LogRedaction.maskRut("12345678-5")).doesNotContain("123456");
        assertThat(LogRedaction.maskRut("12.345.678-5")).isNotEqualTo("12.345.678-5");
    }

    @Test
    void hashSecretIsStableAndNotTheRawValue() {
        String hashed = LogRedaction.hashSecret("idem-secret");
        assertThat(hashed).isEqualTo(LogRedaction.hashSecret("idem-secret"));
        assertThat(hashed).isNotEqualTo("idem-secret");
        assertThat(hashed).hasSize(12);
        assertThat(LogRedaction.hashSecret(" ")).isEqualTo("-");
    }
}