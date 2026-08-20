package com.elicatari.dteissuer.application.fake;

import com.elicatari.dteissuer.application.port.out.FolioRangeRepository;
import com.elicatari.dteissuer.domain.FolioRange;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFolioRangeRepository implements FolioRangeRepository {

    private final ConcurrentHashMap<String, FolioRange> byTenant = new ConcurrentHashMap<>();

    @Override
    public Optional<FolioRange> findByTenantId(TenantId tenantId) {
        return Optional.ofNullable(byTenant.get(tenantId.value()));
    }

    @Override
    public FolioRange save(FolioRange range) {
        byTenant.put(range.tenantId().value(), range);
        return range;
    }

    public void seed(FolioRange range) {
        save(range);
    }
}