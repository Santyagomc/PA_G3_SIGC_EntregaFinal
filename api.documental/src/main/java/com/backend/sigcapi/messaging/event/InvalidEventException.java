package com.backend.sigcapi.messaging.event;

/**
 * El mensaje no cumple el contrato (JSON mal formado, campos obligatorios ausentes, versión no
 * soportada). Es NO reintentable: reintentarlo produciría el mismo error, por lo que el
 * manejador de errores lo envía directamente al Dead Letter Topic.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
