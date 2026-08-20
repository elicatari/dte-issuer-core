package com.elicatari.dteissuer.shared;

import com.elicatari.dteissuer.domain.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant de la petición. Lo llena {@link TenantContextFilter} desde el JWT;
 * el adapter JPA lo exige para activar el {@code @Filter}. ThreadLocal: se limpia
 * al terminar. El MDC lo llena {@link RequestMdcFilter} / el filtro de tenant,
 * no esta clase.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantId tenantId) {
        CURRENT.set(Objects.requireNonNull(tenantId, "tenant_id es obligatorio"));
    }

    public static Optional<TenantId> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantId require() {
        TenantId tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new MissingTenantException();
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}