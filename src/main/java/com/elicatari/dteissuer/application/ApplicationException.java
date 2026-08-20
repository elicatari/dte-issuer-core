package com.elicatari.dteissuer.application;

/**
 * Fallo de aplicación (contrato HTTP, idempotencia). No es invariante de dominio.
 */
public class ApplicationException extends RuntimeException {

    public ApplicationException(String message) {
        super(message);
    }
}