package com.elicatari.dteissuer.adapter.out.persistence;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * TTL de una clave en curso. Prefijo {@code dte.idempotency}.
 */
@Validated
@ConfigurationProperties(prefix = "dte.idempotency")
public record IdempotencyProperties(
        @DefaultValue("60000") @Min(1) long inProgressTtlMs) {}