/**
 * Adapter AMQP: outbox en la misma TX que el DTE; un relay publica a
 * {@code dte.issued} tras commit. No es saga ni segundo proceso (ADR 0002).
 */
package com.elicatari.dteissuer.adapter.out.messaging;