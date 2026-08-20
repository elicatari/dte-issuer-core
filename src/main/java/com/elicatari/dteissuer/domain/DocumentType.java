package com.elicatari.dteissuer.domain;

/**
 * Tipo documental SII. El MVP solo emite Boleta 39.
 */
public enum DocumentType {
    BOLETA_39(39);

    private final int siiCode;

    DocumentType(int siiCode) {
        this.siiCode = siiCode;
    }

    public int siiCode() {
        return siiCode;
    }
}