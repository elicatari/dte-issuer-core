package com.elicatari.dteissuer.application.port.out;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * Store de idempotencia con unicidad {@code (tenant_id, idempotency_key)}.
 *
 * <p>La carrera no se resuelve con un {@code if exists} previo: {@link #tryClaim}
 * intenta insertar; si la unicidad choca, devuelve el registro ya guardado.
 * Una reserva en curso abandonada (adapter: TTL sobre {@code created_at}) se
 * reclama en el mismo {@code INSERT ... ON CONFLICT}.
 */
public interface IdempotencyStore {

    /**
     * Intenta registrar la clave en estado en curso.
     *
     * @return vacío si este llamador adquirió la clave; el registro existente si ya estaba
     */
    Optional<IdempotencyRecord> tryClaim(TenantId tenantId, IdempotencyKey key, String requestHash);

    void complete(TenantId tenantId, IdempotencyKey key, UUID dteId);
}