package com.backend.sigcapi.enums;

/** Ciclo de vida de un evento en la tabla outbox. */
public enum OutboxStatus {
    /** Registrado en la misma transacción del cambio de negocio; pendiente de enviar a Kafka. */
    PENDING,
    /** Confirmado por el broker (acks=all); se guardan partición y offset. */
    PUBLISHED,
    /** Agotó los reintentos de publicación; requiere reintento manual (POST /events/outbox/{id}/retry). */
    FAILED
}
