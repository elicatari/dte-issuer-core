package com.elicatari.dteissuer.adapter.in;

import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
public class HttpJwtTestConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            Instant now = Instant.parse("2026-08-18T20:00:00Z");
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("unused")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .build();
        };
    }
}