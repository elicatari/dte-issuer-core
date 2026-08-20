package com.elicatari.dteissuer.application.port.out;

import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.Optional;

/**
 * Rango de folios (CAF simulado) por tenant. El cursor vive en la fila del rango.
 * El bloqueo bajo contención lo aplica el adapter, no este puerto.
 */
public interface FolioRangeRepository {

    Optional<FolioRange> findByTenantId(TenantId tenantId);

    FolioRange save(FolioRange range);
}