package com.backend.sigcapi.enums;

/** Resultado del envío de una notificación al ciudadano. */
public enum NotificationStatus {
    /** Enviada por SMTP. */
    SENT,
    /** SMTP deshabilitado (MAIL_ENABLED=false): el mensaje se construye y se registra, pero no se envía. */
    SIMULATED
}
