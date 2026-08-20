package com.elicatari.dteissuer.adapter.out.messaging;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Poller in-process del outbox. Prefijo {@code dte.outbox.poller}.
 */
@Validated
@ConfigurationProperties(prefix = "dte.outbox.poller")
public record OutboxPollerProperties(
        @DefaultValue("2000") @Min(1) long delayMs,
        @DefaultValue("2000") @Min(0) long graceMs,
        @DefaultValue("50") @Min(1) int batchSize) {}