package com.elicatari.dteissuer.shared;

import com.elicatari.dteissuer.domain.TenantId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Lee {@code tenant_id} del JWT. Nunca de un header ni del body.
 */
public final class JwtTenantClaim {

    public static final String CLAIM = "tenant_id";

    private JwtTenantClaim() {}

    public static Optional<TenantId> from(Authentication authentication) {
        return jwtOf(authentication).flatMap(JwtTenantClaim::from);
    }

    public static boolean isJwt(Authentication authentication) {
        return jwtOf(authentication).isPresent();
    }

    public static Optional<TenantId> from(Jwt jwt) {
        String raw = rawValue(jwt.getClaim(CLAIM));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TenantId(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Jwt> jwtOf(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    private static String rawValue(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof String text) {
            return blankToNull(text);
        }
        if (claim instanceof Collection<?> values) {
            if (values.isEmpty()) {
                return null;
            }
            Object first = values.iterator().next();
            return first == null ? null : blankToNull(first.toString());
        }
        return blankToNull(claim.toString());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}