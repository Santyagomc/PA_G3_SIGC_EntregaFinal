package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestEventType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.event.EventCodec;
import com.backend.sigcapi.messaging.event.InvalidEventException;
import com.backend.sigcapi.messaging.event.RequestEvent;

/** Contrato del evento: serialización, lectura tolerante y validaciones (no reintentables). */
class EventCodecTest {

    private final EventCodec codec = new EventCodec();

    @Test
    @DisplayName("ida y vuelta: el JSON conserva todos los campos del contrato v1")
    void roundTrip() {
        RequestEvent original = RequestEvent.statusChanged(42L, RequesType.RECLAMO, RequestStatus.CREADO,
                RequestStatus.CERRADO, "1000000001", 1L, "Resuelto");

        String json = codec.toJson(original);
        RequestEvent back = codec.fromJson(json);

        assertEquals(original, back);
        assertTrue(json.contains("\"eventType\":\"REQUEST_STATUS_CHANGED\""));
        assertTrue(json.contains("\"estadoAnterior\":\"CREADO\""));
        assertTrue(json.contains("\"estadoNuevo\":\"CERRADO\""));
        assertTrue(json.contains("\"eventVersion\":1"));
    }

    @Test
    @DisplayName("el evento NO incluye datos personales del ciudadano (Ley 1581)")
    void sinDatosPersonales() {
        String json = codec.toJson(RequestEvent.created(7L, RequesType.QUEJA, RequestStatus.CREADO, "1000000001", 1L));
        assertFalse(json.contains("email"));
        assertFalse(json.contains("phone"));
        assertFalse(json.contains("fullName"));
    }

    @Test
    @DisplayName("REQUEST_CREATED es válido sin estadoAnterior")
    void creadoSinEstadoAnterior() {
        RequestEvent created = RequestEvent.created(7L, RequesType.QUEJA, RequestStatus.CREADO, "1000000001", 1L);
        RequestEvent back = codec.fromJson(codec.toJson(created));
        assertEquals(RequestEventType.REQUEST_CREATED, back.eventType());
        assertNull(back.estadoAnterior());
    }

    @Test
    @DisplayName("lector tolerante: ignora campos desconocidos (evolución compatible del contrato)")
    void ignoraCamposDesconocidos() {
        String json = codec.toJson(RequestEvent.created(7L, RequesType.QUEJA, RequestStatus.CREADO, "doc", 1L));
        String extendido = json.substring(0, json.length() - 1) + ",\"campoFuturo\":\"x\"}";
        assertEquals(7L, codec.fromJson(extendido).requestId());
    }

    @Test
    @DisplayName("JSON mal formado ⇒ InvalidEventException")
    void jsonMalFormado() {
        assertThrows(InvalidEventException.class, () -> codec.fromJson("{\"eventId\": \"abc\", "));
    }

    @Test
    @DisplayName("mensaje vacío o nulo ⇒ InvalidEventException")
    void mensajeVacio() {
        assertThrows(InvalidEventException.class, () -> codec.fromJson(""));
        assertThrows(InvalidEventException.class, () -> codec.fromJson(null));
    }

    @Test
    @DisplayName("estado inexistente en el enum ⇒ InvalidEventException")
    void estadoInvalido() {
        String json = codec.toJson(RequestEvent.statusChanged(1L, RequesType.QUEJA, RequestStatus.CREADO,
                RequestStatus.CERRADO, "doc", 1L, null)).replace("\"CERRADO\"", "\"ARCHIVADO\"");
        assertThrows(InvalidEventException.class, () -> codec.fromJson(json));
    }

    @Test
    @DisplayName("faltan campos obligatorios ⇒ InvalidEventException con el campo en el mensaje")
    void camposObligatorios() {
        InvalidEventException ex = assertThrows(InvalidEventException.class, () -> codec.fromJson(
                "{\"eventId\":\"2f0b1c4e-8a7d-4a4b-9a55-0e6b3f3f8d11\",\"eventType\":\"REQUEST_STATUS_CHANGED\",\"eventVersion\":1}"));
        assertTrue(ex.getMessage().contains("requestId"));
    }

    @Test
    @DisplayName("eventId que no es UUID ⇒ InvalidEventException")
    void eventIdNoUuid() {
        RequestEvent e = new RequestEvent("no-uuid", RequestEventType.REQUEST_CREATED, 1, "s",
                "2026-10-05T10:00:00Z", 1L, RequesType.QUEJA, null, RequestStatus.CREADO, "doc", 1L, null);
        assertThrows(InvalidEventException.class, () -> codec.validate(e));
    }

    @Test
    @DisplayName("versión de contrato no soportada ⇒ InvalidEventException")
    void versionNoSoportada() {
        RequestEvent e = new RequestEvent("2f0b1c4e-8a7d-4a4b-9a55-0e6b3f3f8d11", RequestEventType.REQUEST_CREATED, 99,
                "s", "2026-10-05T10:00:00Z", 1L, RequesType.QUEJA, null, RequestStatus.CREADO, "doc", 1L, null);
        assertThrows(InvalidEventException.class, () -> codec.validate(e));
    }

    @Test
    @DisplayName("REQUEST_STATUS_CHANGED sin estadoAnterior ⇒ InvalidEventException")
    void cambioSinEstadoAnterior() {
        RequestEvent e = new RequestEvent("2f0b1c4e-8a7d-4a4b-9a55-0e6b3f3f8d11", RequestEventType.REQUEST_STATUS_CHANGED,
                1, "s", "2026-10-05T10:00:00Z", 1L, RequesType.QUEJA, null, RequestStatus.CERRADO, "doc", 1L, null);
        assertThrows(InvalidEventException.class, () -> codec.validate(e));
    }

    @Test
    @DisplayName("timestamp que no es ISO-8601 ⇒ InvalidEventException")
    void timestampInvalido() {
        RequestEvent e = new RequestEvent("2f0b1c4e-8a7d-4a4b-9a55-0e6b3f3f8d11", RequestEventType.REQUEST_CREATED, 1,
                "s", "05/10/2026", 1L, RequesType.QUEJA, null, RequestStatus.CREADO, "doc", 1L, null);
        assertThrows(InvalidEventException.class, () -> codec.validate(e));
    }
}
