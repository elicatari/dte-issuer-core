package com.elicatari.dteissuer.application.port.out;

import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de DTE. Toda carga lleva {@code tenant_id}; el adapter no expone
 * un {@code find} sin tenant.
 */
public interface DteRepository {

    Dte save(Dte dte);

    Optional<Dte> findById(TenantId tenantId, UUID id);

    List<Dte> findByTenantId(TenantId tenantId);
}