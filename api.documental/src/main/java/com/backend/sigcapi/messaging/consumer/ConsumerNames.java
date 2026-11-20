package com.backend.sigcapi.messaging.consumer;

/** Nombre lógico de cada consumidor; se usa como clave de idempotencia en processed_events. */
public final class ConsumerNames {

    public static final String AUDIT = "audit-history";
    public static final String NOTIFICATIONS = "citizen-notifications";

    public static final String OUTCOME_PROCESSED = "PROCESADO";
    public static final String OUTCOME_SKIPPED = "OMITIDO";

    private ConsumerNames() {
    }
}
