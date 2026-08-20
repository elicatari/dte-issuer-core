package com.elicatari.dteissuer.shared;

import com.elicatari.dteissuer.domain.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copia {@code tenant_id} del JWT a {@link TenantContext}. Un header o query
 * de tenant se rechaza: cambio de empresa es logout + login.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    static final String HEADER_X_TENANT_ID = "X-Tenant-Id";
    static final String HEADER_TENANT_ID = "tenant_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (isDteApi(request) && !bindTenantFromJwt(request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * @return {@code false} si la petición ya quedó respondida (400/401)
     */
    private static boolean bindTenantFromJwt(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!JwtTenantClaim.isJwt(authentication)) {
            return true;
        }
        if (clientSentTenant(request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        Optional<TenantId> tenantId = JwtTenantClaim.from(authentication);
        if (tenantId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        TenantContext.set(tenantId.get());
        RequestMdc.putTenantId(tenantId.get().value());
        return true;
    }

    private static boolean isDteApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/v1/dte") || path.startsWith("/api/v1/dte/");
    }

    private static boolean clientSentTenant(HttpServletRequest request) {
        return hasHeader(request, HEADER_X_TENANT_ID)
                || hasHeader(request, HEADER_TENANT_ID)
                || request.getParameter(HEADER_TENANT_ID) != null;
    }

    private static boolean hasHeader(HttpServletRequest request, String name) {
        return request.getHeader(name) != null;
    }
}