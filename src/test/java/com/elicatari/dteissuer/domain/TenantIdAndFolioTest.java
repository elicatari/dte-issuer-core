package com.elicatari.dteissuer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantIdAndFolioTest {

    @Test
    void tenantIdRejectsBlank() {
        assertThatThrownBy(() -> new TenantId("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TenantId(" alpha ").value()).isEqualTo("alpha");
    }

    @Test
    void folioRejectsZero() {
        assertThatThrownBy(() -> new Folio(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Folio(1).next()).isEqualTo(new Folio(2));
        assertThat(new Folio(3).isAfter(new Folio(2))).isTrue();
    }

    @Test
    void documentTypeIsBoleta39() {
        assertThat(DocumentType.BOLETA_39.siiCode()).isEqualTo(39);
    }
}