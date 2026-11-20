package com.backend.sigcapi.enums;

/**
 * Tipos de evento de dominio publicados en el tópico {@code audit-events}.
 * Cada valor corresponde a un hecho de negocio ya confirmado en base de datos.
 */
public enum RequestEventType {
    /** Se radicó una nueva PQR (estado inicial CREADO). */
    REQUEST_CREATED,
    /** Un administrador cambió el estado de una PQR. */
    REQUEST_STATUS_CHANGED
}
