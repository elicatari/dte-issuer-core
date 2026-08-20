package com.elicatari.dteissuer.shared;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KeycloakHealthIndicator implements HealthIndicator {

    private final RestClient restClient;
    private final String jwkSetUri;

    public KeycloakHealthIndicator(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Health health() {
        try {
            restClient.get().uri(jwkSetUri).retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}