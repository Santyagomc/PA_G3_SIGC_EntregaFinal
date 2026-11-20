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
 * Consumidor 2 — grupo {@code sigc-notifications}: notifica al ciudadano. Al ser un grupo
 * distinto del de auditoría, recibe TODOS los eventos del tópico (fan-out / pub-sub) y avanza
 * a su propio ritmo: una caída del SMTP no retrasa la auditoría, y viceversa.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final EventCodec codec;
    private final NotificationProcessor processor;
    private final ProcessedEventRepository processedEvents;

    public NotificationEventConsumer(EventCodec codec, NotificationProcessor processor,
            ProcessedEventRepository processedEvents) {
        this.codec = codec;
        this.processor = processor;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "citizenNotificationListener",
            topics = "${app.kafka.topics.request-events:audit-events}",
            groupId = "${app.kafka.consumers.notifications-group:sigc-notifications}",
            concurrency = "${app.kafka.consumers.concurrency:3}",
            autoStartup = "${app.kafka.listeners.auto-startup:true}")
    public void onEvent(ConsumerRecord<String, String> record) {
        log.info("[KAFKA-CONSUMER:{}] Recibido {}-{}@{} key={}", ConsumerNames.NOTIFICATIONS,
                record.topic(), record.partition(), record.offset(), record.key());

        RequestEvent event = codec.fromJson(record.value());
        try (MDC.MDCCloseable e = MDC.putCloseable("eventId", event.eventId());
                MDC.MDCCloseable r = MDC.putCloseable("requestId", String.valueOf(event.requestId()));
                MDC.MDCCloseable c = MDC.putCloseable("correlationId", com.backend.sigcapi.config.CorrelationIdFilter.fromRecord(record))) {
            try {
                processor.process(event);
            } catch (DataIntegrityViolationException ex) {
                if (processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)) {
                    log.info("[NOTIFICACIONES] Duplicado concurrente detectado por la restricción única; se ignora eventId={}",
                            event.eventId());
                    return;
                }
                throw ex;
            }
        }
    }
}
