package com.elicatari.dteissuer.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.elicatari.dteissuer.domain.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtTenantClaimTest {

    @Test
    void stringClaimBecomesTenant() {
        assertThat(JwtTenantClaim.from(jwt("alpha"))).contains(new TenantId("alpha"));
    }

    @Test
    void listClaimUsesFirstValue() {
        Jwt jwt = baseJwt().claim(JwtTenantClaim.CLAIM, List.of("beta")).build();
        assertThat(JwtTenantClaim.from(jwt)).contains(new TenantId("beta"));
    }

    @Test
    void missingOrBlankClaimIsEmpty() {
        assertThat(JwtTenantClaim.from(baseJwt().build())).isEmpty();
        assertThat(JwtTenantClaim.from(baseJwt().claim(JwtTenantClaim.CLAIM, "  ").build())).isEmpty();
        assertThat(JwtTenantClaim.from(baseJwt().claim(JwtTenantClaim.CLAIM, List.of()).build())).isEmpty();
    }

    @Test
    void anonymousIsNotJwt() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(JwtTenantClaim.isJwt(anonymous)).isFalse();
        assertThat(JwtTenantClaim.from(anonymous)).isEmpty();
    }

    @Test
    void jwtAuthenticationWithoutClaimIsStillJwt() {
        JwtAuthenticationToken token = new JwtAuthenticationToken(baseJwt().build());
        assertThat(JwtTenantClaim.isJwt(token)).isTrue();
        assertThat(JwtTenantClaim.from(token)).isEmpty();
    }

    private static Jwt jwt(String tenantId) {
        return baseJwt().claim(JwtTenantClaim.CLAIM, tenantId).build();
    }

    private static Jwt.Builder baseJwt() {
        Instant now = Instant.parse("2026-08-18T20:00:00Z");
        return Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("user_alpha")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}