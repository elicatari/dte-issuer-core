package com.elicatari.dteissuer.application.fake;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.out.IdempotencyRecord;
import com.elicatari.dteissuer.application.port.out.IdempotencyStore;
import com.elicatari.dteissuer.domain.TenantId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unicidad con {@code putIfAbsent}: quien pierde relee el registro, no inserta otro.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> tryClaim(TenantId tenantId, IdempotencyKey key, String requestHash) {
        IdempotencyRecord incoming = IdempotencyRecord.started(tenantId, key.value(), requestHash);
        IdempotencyRecord existing = records.putIfAbsent(scope(tenantId, key), incoming);
        return Optional.ofNullable(existing);
    }

    @Override
    public void complete(TenantId tenantId, IdempotencyKey key, UUID dteId) {
        records.computeIfPresent(scope(tenantId, key), (ignored, current) -> current.completed(dteId));
    }

    public void seed(IdempotencyRecord record) {
        records.put(scope(record.tenantId(), new IdempotencyKey(record.key())), record);
    }

    public Optional<IdempotencyRecord> find(TenantId tenantId, IdempotencyKey key) {
        return Optional.ofNullable(records.get(scope(tenantId, key)));
    }

    private static String scope(TenantId tenantId, IdempotencyKey key) {
        return tenantId.value() + '\0' + key.value();
    }
}