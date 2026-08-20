package com.elicatari.dteissuer.domain;

/**
 * Fallo de invariante de negocio. No es error de infraestructura.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}