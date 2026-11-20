package com.backend.sigcapi.messaging.event;

/**
 * Evento válido sintácticamente pero imposible de procesar por una condición de negocio
 * permanente (por ejemplo, la PQR o el actor no existen). No se reintenta: va al DLT.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }
}
