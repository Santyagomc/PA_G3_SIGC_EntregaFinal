package com.backend.sigcapi.messaging.event;

/** Encabezados (headers) Kafka que acompañan a cada evento publicado. */
public final class EventHeaders {

    public static final String EVENT_ID = "x-event-id";
    public static final String EVENT_TYPE = "x-event-type";
    public static final String EVENT_VERSION = "x-event-version";
    public static final String SOURCE = "x-source";
    /** Marca los mensajes generados por el simulador (endpoints /events/simulate/**). */
    public static final String SIMULATED = "x-simulated";
    /** Identificador de correlación propagado desde el gateway/API (trazabilidad de extremo a extremo). */
    public static final String CORRELATION_ID = "x-correlation-id";

    private EventHeaders() {
    }
}
