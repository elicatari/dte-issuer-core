package com.elicatari.dteissuer.adapter.in;

/**
 * Body de POST /api/v1/dte. El tenant no viaja aquí: sale del JWT.
 */
public record IssueDteRequest(String rut, Long neto) {}