package com.elicatari.dteissuer.application.fake;

import com.elicatari.dteissuer.application.port.out.DteRepository;
import com.elicatari.dteissuer.domain.Dte;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDteRepository implements DteRepository {

    private final ConcurrentHashMap<UUID, Dte> byId = new ConcurrentHashMap<>();

    @Override
    public Dte save(Dte dte) {
        byId.put(dte.id(), dte);
        return dte;
    }

    @Override
    public Optional<Dte> findById(TenantId tenantId, UUID id) {
        return Optional.ofNullable(byId.get(id)).filter(dte -> dte.tenantId().equals(tenantId));
    }

    @Override
    public List<Dte> findByTenantId(TenantId tenantId) {
        return byId.values().stream()
                .filter(dte -> dte.tenantId().equals(tenantId))
                .toList();
    }

    public Collection<Dte> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}