package com.elicatari.dteissuer.adapter.out.messaging;

/**
 * Contrato de la cola. El nombre y la versión van en propiedades del mensaje.
 */
public final class DteIssuedQueues {

    public static final String NAME = "dte.issued";
    public static final String EVENT_NAME = "DteIssued";
    public static final String EVENT_VERSION = "1";

    public static final String HEADER_EVENT_NAME = "eventName";
    public static final String HEADER_EVENT_VERSION = "eventVersion";

    private DteIssuedQueues() {}
}