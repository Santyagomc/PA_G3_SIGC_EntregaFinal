package com.backend.sigcapi.messaging.event;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.backend.sigcapi.enums.RequestEventType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializa y valida los eventos. Los mensajes viajan como JSON (String) y la deserialización
 * se hace aquí —no en el deserializador de Kafka— para poder clasificar el error como
 * {@link InvalidEventException} (no reintentable) y conservar el mensaje original en el DLT.
 *
 * <p>Lector tolerante: ignora campos desconocidos para permitir evolución compatible del
 * contrato (agregar campos no rompe a los consumidores existentes).</p>
 */
@Component
public class EventCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String toJson(RequestEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.eventId(), e);
        }
    }

    public RequestEvent fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidEventException("Mensaje vacío: no contiene un evento");
        }
        RequestEvent event;
        try {
            event = mapper.readValue(json, RequestEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("JSON inválido o tipos incompatibles con el contrato: " + e.getMessage(), e);
        }
        validate(event);
        return event;
    }

    /** Reglas del contrato v1 (ver contracts/events/request-event.schema.json). */
    public void validate(RequestEvent e) {
        if (e == null) {
            throw new InvalidEventException("El mensaje no contiene un evento");
        }
        if (isBlank(e.eventId())) {
            throw new InvalidEventException("eventId es obligatorio");
        }
        try {
            UUID.fromString(e.eventId());
        } catch (IllegalArgumentException ex) {
            throw new InvalidEventException("eventId debe ser un UUID: " + e.eventId());
        }
        if (e.eventType() == null) {
            throw new InvalidEventException("eventType es obligatorio");
        }
        if (e.eventVersion() == null || e.eventVersion() < 1 || e.eventVersion() > RequestEvent.CURRENT_VERSION) {
            throw new InvalidEventException("eventVersion no soportada: " + e.eventVersion());
        }
        if (e.requestId() == null || e.requestId() <= 0) {
            throw new InvalidEventException("requestId es obligatorio y debe ser positivo");
        }
        if (isBlank(e.timestamp())) {
            throw new InvalidEventException("timestamp es obligatorio");
        }
        try {
            Instant.parse(e.timestamp());
        } catch (DateTimeParseException ex) {
            throw new InvalidEventException("timestamp debe estar en formato ISO-8601 UTC: " + e.timestamp());
        }
        if (e.estadoNuevo() == null) {
            throw new InvalidEventException("estadoNuevo es obligatorio");
        }
        if (e.eventType() == RequestEventType.REQUEST_STATUS_CHANGED && e.estadoAnterior() == null) {
            throw new InvalidEventException("estadoAnterior es obligatorio en REQUEST_STATUS_CHANGED");
        }
        if (e.actorId() == null) {
            throw new InvalidEventException("actorId es obligatorio");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
