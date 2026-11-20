package com.backend.sigcapi.messaging.producer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.exceptions.BusinessRuleException;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.messaging.event.EventCodec;
import com.backend.sigcapi.messaging.event.EventHeaders;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.OutboxEventRepository;

/**
 * Simulador de eventos para la sustentación y las pruebas. Publica DIRECTAMENTE en Kafka
 * (sin outbox) escenarios que el flujo normal nunca produce, para demostrar idempotencia y
 * manejo de errores en ejecución:
 * <ol>
 *   <li>duplicate: reenvía byte a byte un evento ya publicado ⇒ los consumidores lo ignoran.</li>
 *   <li>invalid-payload: JSON mal formado ⇒ DLT sin reintentos.</li>
 *   <li>contract-violation: JSON válido sin campos obligatorios ⇒ DLT sin reintentos.</li>
 *   <li>unknown-request: evento válido de una PQR inexistente ⇒ DLT sin reintentos.</li>
 * </ol>
 */
@Service
public class EventSimulator {

    private static final Logger log = LoggerFactory.getLogger(EventSimulator.class);
    public static final long UNKNOWN_REQUEST_ID = 999_999_999L;

    private final OutboxEventRepository outboxRepository;
    private final KafkaEventSender sender;
    private final EventCodec codec;
    private final String topic;

    public EventSimulator(OutboxEventRepository outboxRepository, KafkaEventSender sender, EventCodec codec,
            @Value("${app.kafka.topics.request-events:audit-events}") String topic) {
        this.outboxRepository = outboxRepository;
        this.sender = sender;
        this.codec = codec;
        this.topic = topic;
    }

    public record SimulationResult(String scenario, String topic, int partition, long offset, String key,
            String payload, String expectedOutcome) {
    }

    public SimulationResult duplicate(String eventId) {
        OutboxEvent original = outboxRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe en el outbox el evento " + eventId));
        if (original.getStatus() != OutboxStatus.PUBLISHED) {
            throw new BusinessRuleException("Solo se puede duplicar un evento ya publicado (estado actual: "
                    + original.getStatus() + ")");
        }
        Map<String, String> headers = baseHeaders(original.getEventId(), original.getEventType().name());
        return send("DUPLICATE", original.getEventKey(), original.getPayload(), headers,
                "Ambos consumidores registran 'Evento duplicado ignorado'; request_history y notifications no cambian.");
    }

    public SimulationResult invalidPayload() {
        String payload = "{\"eventId\": \"" + UUID.randomUUID() + "\", \"eventType\": \"REQUEST_STATUS_CHANGED\", ";
        return send("INVALID_PAYLOAD", "simulador", payload, baseHeaders(null, "UNKNOWN"),
                "InvalidEventException sin reintentos; el mensaje llega a audit-events.DLT y se guarda en failed_events.");
    }

    public SimulationResult contractViolation() {
        String payload = "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"REQUEST_STATUS_CHANGED\",\"eventVersion\":1,\"source\":\"simulador\"}";
        return send("CONTRACT_VIOLATION", "simulador", payload, baseHeaders(null, "REQUEST_STATUS_CHANGED"),
                "InvalidEventException (faltan requestId, timestamp, estados y actorId); va al DLT sin reintentos.");
    }

    public SimulationResult unknownRequest(Long actorId) {
        RequestEvent event = RequestEvent.statusChanged(UNKNOWN_REQUEST_ID, RequesType.QUEJA, RequestStatus.CREADO,
                RequestStatus.CERRADO, "simulador", actorId, "Evento simulado de una PQR inexistente");
        return send("UNKNOWN_REQUEST", String.valueOf(UNKNOWN_REQUEST_ID), codec.toJson(event),
                baseHeaders(event.eventId(), event.eventType().name()),
                "NonRetryableEventException ('La PQR " + UNKNOWN_REQUEST_ID + " no existe'); va al DLT sin reintentos.");
    }

    private Map<String, String> baseHeaders(String eventId, String eventType) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (eventId != null) {
            headers.put(EventHeaders.EVENT_ID, eventId);
        }
        headers.put(EventHeaders.EVENT_TYPE, eventType);
        headers.put(EventHeaders.EVENT_VERSION, String.valueOf(RequestEvent.CURRENT_VERSION));
        headers.put(EventHeaders.SOURCE, "sigcapi.simulator");
        headers.put(EventHeaders.SIMULATED, "true");
        return headers;
    }

    private SimulationResult send(String scenario, String key, String payload, Map<String, String> headers,
            String expected) {
        try {
            KafkaEventSender.Delivery d = sender.send(topic, key, payload, headers);
            log.warn("[SIMULADOR] Escenario {} publicado en {}-{}@{} key={}", scenario, d.topic(), d.partition(),
                    d.offset(), key);
            return new SimulationResult(scenario, d.topic(), d.partition(), d.offset(), key, payload, expected);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulación interrumpida", e);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar en Kafka: " + e.getMessage(), e);
        }
    }
}
