package com.elicatari.dteissuer.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body de POST /api/v1/dte. El tenant no viaja aquí: sale del JWT.
 */
@Schema(description = "Body de emisión. El tenant no viaja aquí: sale del JWT.")
public record IssueDteRequest(
        @Schema(description = "RUT del receptor", example = "12.345.678-5") String rut,
        @Schema(description = "Neto en pesos, escala 0", example = "1000") Long neto) {}