package com.elicatari.dteissuer.shared;

/**
 * Sesión JPA sin tenant: falla cerrada, no lee filas de todos.
 */
public class MissingTenantException extends IllegalStateException {

    public MissingTenantException() {
        super("tenant_id ausente: la sesión no puede leer datos");
    }
}