package com.backend.sigcapi.messaging.producer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.messaging.event.EventHeaders;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.OutboxEventRepository;

/**
 * Productor (fase 2 de 2): cada {@code app.kafka.outbox.relay-interval-ms} toma los eventos
 * PENDING en orden de creación y los publica en Kafka con la clave = requestId.
 *
 * <ul>
 *   <li>Éxito: el evento pasa a PUBLISHED y se guardan partición y offset (trazabilidad).</li>
 *   <li>Fallo (broker caído, timeout): se incrementa {@code attempts}, se guarda el error y se
 *       DETIENE el lote para no publicar eventos posteriores antes que uno anterior (orden).</li>
 *   <li>Al agotar {@code max-attempts} el evento pasa a FAILED y se puede reintentar a mano.</li>
 * </ul>
 * Garantía resultante: entrega "al menos una vez" ⇒ los consumidores son idempotentes.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaEventSender sender;
    private final int maxAttempts;

    public OutboxRelay(OutboxEventRepository repository, KafkaEventSender sender,
            @Value("${app.kafka.outbox.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.sender = sender;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.relay-interval-ms:1000}",
            initialDelayString = "${app.kafka.outbox.relay-initial-delay-ms:5000}")
    public void publishPending() {
        List<OutboxEvent> batch = repository.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("[OUTBOX-RELAY] {} evento(s) pendiente(s)", batch.size());
        for (OutboxEvent event : batch) {
            if (!publish(event)) {
                break;
            }
        }
    }

    /** Publica un evento; devuelve {@code false} si falló (el lote se detiene). */
    boolean publish(OutboxEvent event) {
        MDC.put("eventId", event.getEventId());
        if (event.getCorrelationId() != null) {
            MDC.put("correlationId", event.getCorrelationId());
        }
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(EventHeaders.EVENT_ID, event.getEventId());
            headers.put(EventHeaders.EVENT_TYPE, event.getEventType().name());
            headers.put(EventHeaders.EVENT_VERSION, String.valueOf(RequestEvent.CURRENT_VERSION));
            headers.put(EventHeaders.SOURCE, RequestEvent.SOURCE);
            headers.put(EventHeaders.CORRELATION_ID, event.getCorrelationId());

            KafkaEventSender.Delivery delivery = sender.send(event.getTopic(), event.getEventKey(), event.getPayload(), headers);

            event.markPublished(delivery.partition(), delivery.offset());
            repository.save(event);
            log.info("[KAFKA-PRODUCER] Evento publicado eventId={} type={} key={} -> {}-{}@{}",
                    event.getEventId(), event.getEventType(), event.getEventKey(),
                    delivery.topic(), delivery.partition(), delivery.offset());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleFailure(event, e);
            return false;
        } catch (Exception e) {
            handleFailure(event, e);
            return false;
        } finally {
            MDC.remove("eventId");
            MDC.remove("correlationId");
        }
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        Throwable root = e.getCause() != null ? e.getCause() : e;
        String error = root.getClass().getSimpleName() + ": " + root.getMessage();
        boolean exhausted = event.registerFailure(error, maxAttempts);
        repository.save(event);
        if (exhausted) {
            log.error("[KAFKA-PRODUCER] Evento {} marcado FAILED tras {} intentos. Último error: {}",
                    event.getEventId(), event.getAttempts(), error);
        } else {
            log.warn("[KAFKA-PRODUCER] No se pudo publicar eventId={} (intento {}/{}): {}. Se reintentará.",
                    event.getEventId(), event.getAttempts(), maxAttempts, error);
        }
    }
}
