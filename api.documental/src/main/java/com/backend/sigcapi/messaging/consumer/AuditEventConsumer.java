package com.backend.sigcapi.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.backend.sigcapi.messaging.event.EventCodec;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.ProcessedEventRepository;

/**
 * Consumidor 1 — grupo {@code sigc-audit}: construye el historial de auditoría de cada PQR
 * (responsabilidad aprobada en la arquitectura para el tópico audit-events).
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final EventCodec codec;
    private final AuditEventProcessor processor;
    private final ProcessedEventRepository processedEvents;

    public AuditEventConsumer(EventCodec codec, AuditEventProcessor processor, ProcessedEventRepository processedEvents) {
        this.codec = codec;
        this.processor = processor;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "auditHistoryListener",
            topics = "${app.kafka.topics.request-events:audit-events}",
            groupId = "${app.kafka.consumers.audit-group:sigc-audit}",
            concurrency = "${app.kafka.consumers.concurrency:3}",
            autoStartup = "${app.kafka.listeners.auto-startup:true}")
    public void onEvent(ConsumerRecord<String, String> record) {
        log.info("[KAFKA-CONSUMER:{}] Recibido {}-{}@{} key={}", ConsumerNames.AUDIT,
                record.topic(), record.partition(), record.offset(), record.key());

        RequestEvent event = codec.fromJson(record.value()); // InvalidEventException => DLT sin reintentos
        try (MDC.MDCCloseable e = MDC.putCloseable("eventId", event.eventId());
                MDC.MDCCloseable r = MDC.putCloseable("requestId", String.valueOf(event.requestId()));
                MDC.MDCCloseable c = MDC.putCloseable("correlationId", com.backend.sigcapi.config.CorrelationIdFilter.fromRecord(record))) {
            try {
                processor.process(event);
            } catch (DataIntegrityViolationException ex) {
                // Carrera entre dos entregas del mismo evento: la restricción única lo detectó.
                if (processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.AUDIT)) {
                    log.info("[AUDITORIA] Duplicado concurrente detectado por la restricción única; se ignora eventId={}",
                            event.eventId());
                    return;
                }
                throw ex;
            }
        }
    }
}
