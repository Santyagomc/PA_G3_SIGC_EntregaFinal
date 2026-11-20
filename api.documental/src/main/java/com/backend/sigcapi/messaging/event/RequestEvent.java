package com.backend.sigcapi.messaging.event;

import java.time.Instant;
import java.util.UUID;

import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestEventType;
import com.backend.sigcapi.enums.RequestStatus;

/**
 * Contrato del evento publicado en el tópico {@code audit-events} (versión 1).
 *
 * <p>Evoluciona el {@code AuditEvent} aprobado en la arquitectura (requestId, estadoAnterior,
 * estadoNuevo, actor, timestamp) con metadatos de mensajería: {@code eventId} (clave de
 * idempotencia), {@code eventType}, {@code eventVersion} y {@code source}.</p>
 *
 * <p>Por la Ley 1581 de 2012 (habeas data) el evento NO transporta datos personales del
 * ciudadano (nombre, correo, teléfono): los consumidores los consultan por {@code requestId}.
 * Contrato formal: {@code contracts/asyncapi.yaml} y {@code contracts/events/request-event.schema.json}.</p>
 */
public record RequestEvent(
        String eventId,
        RequestEventType eventType,
        Integer eventVersion,
        String source,
        String timestamp,
        Long requestId,
        RequesType tipoSolicitud,
        RequestStatus estadoAnterior,
        RequestStatus estadoNuevo,
        String actor,
        Long actorId,
        String observaciones) {

    public static final int CURRENT_VERSION = 1;
    public static final String SOURCE = "sigcapi.requests";

    public static RequestEvent created(Long requestId, RequesType type, RequestStatus initialStatus,
            String actorDocument, Long actorId) {
        return new RequestEvent(UUID.randomUUID().toString(), RequestEventType.REQUEST_CREATED, CURRENT_VERSION,
                SOURCE, Instant.now().toString(), requestId, type, null, initialStatus, actorDocument, actorId, null);
    }

    public static RequestEvent statusChanged(Long requestId, RequesType type, RequestStatus oldStatus,
            RequestStatus newStatus, String actorDocument, Long actorId, String observations) {
        return new RequestEvent(UUID.randomUUID().toString(), RequestEventType.REQUEST_STATUS_CHANGED, CURRENT_VERSION,
                SOURCE, Instant.now().toString(), requestId, type, oldStatus, newStatus, actorDocument, actorId,
                observations);
    }
}
