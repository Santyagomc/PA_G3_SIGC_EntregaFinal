package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.KafkaHeaders;

import com.backend.sigcapi.entities.FailedEvent;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.consumer.AuditEventConsumer;
import com.backend.sigcapi.messaging.consumer.AuditEventProcessor;
import com.backend.sigcapi.messaging.consumer.ConsumerNames;
import com.backend.sigcapi.messaging.consumer.DeadLetterConsumer;
import com.backend.sigcapi.messaging.consumer.NotificationEventConsumer;
import com.backend.sigcapi.messaging.consumer.NotificationProcessor;
import com.backend.sigcapi.messaging.event.EventCodec;
import com.backend.sigcapi.messaging.event.InvalidEventException;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.FailedEventRepository;
import com.backend.sigcapi.repositories.ProcessedEventRepository;

/** Listeners Kafka: deserialización, clasificación de errores y carreras de duplicados. */
@ExtendWith(MockitoExtension.class)
class EventConsumersTest {

    @Mock private AuditEventProcessor auditProcessor;
    @Mock private NotificationProcessor notificationProcessor;
    @Mock private ProcessedEventRepository processedEvents;
    @Mock private FailedEventRepository failedEvents;
    private final EventCodec codec = new EventCodec();

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("audit-events", 1, 25L, "5", value);
    }

    private final RequestEvent event = RequestEvent.statusChanged(5L, RequesType.QUEJA, RequestStatus.CREADO,
            RequestStatus.CERRADO, "1000000001", 1L, null);

    @Test
    @DisplayName("auditoría: un mensaje válido se entrega al procesador")
    void auditValido() {
        AuditEventConsumer consumer = new AuditEventConsumer(codec, auditProcessor, processedEvents);
        consumer.onEvent(record(codec.toJson(event)));
        verify(auditProcessor).process(event);
    }

    @Test
    @DisplayName("auditoría: JSON inválido ⇒ InvalidEventException (no reintentable ⇒ DLT)")
    void auditInvalido() {
        AuditEventConsumer consumer = new AuditEventConsumer(codec, auditProcessor, processedEvents);
        assertThrows(InvalidEventException.class, () -> consumer.onEvent(record("no es json")));
        verifyNoInteractions(auditProcessor);
    }

    @Test
    @DisplayName("auditoría: violación de unicidad por duplicado concurrente se trata como duplicado")
    void auditCarreraDuplicado() {
        AuditEventConsumer consumer = new AuditEventConsumer(codec, auditProcessor, processedEvents);
        when(auditProcessor.process(any())).thenThrow(new DataIntegrityViolationException("uk_processed_event_consumer"));
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.AUDIT)).thenReturn(true);

        assertDoesNotThrow(() -> consumer.onEvent(record(codec.toJson(event))));
    }

    @Test
    @DisplayName("auditoría: violación de integridad que NO es duplicado se propaga (reintento)")
    void auditViolacionReal() {
        AuditEventConsumer consumer = new AuditEventConsumer(codec, auditProcessor, processedEvents);
        when(auditProcessor.process(any())).thenThrow(new DataIntegrityViolationException("otra"));
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.AUDIT)).thenReturn(false);

        assertThrows(DataIntegrityViolationException.class, () -> consumer.onEvent(record(codec.toJson(event))));
    }

    @Test
    @DisplayName("notificaciones: un mensaje válido se entrega al procesador")
    void notificacionValida() {
        NotificationEventConsumer consumer = new NotificationEventConsumer(codec, notificationProcessor, processedEvents);
        consumer.onEvent(record(codec.toJson(event)));
        verify(notificationProcessor).process(event);
    }

    @Test
    @DisplayName("notificaciones: JSON inválido ⇒ InvalidEventException")
    void notificacionInvalida() {
        NotificationEventConsumer consumer = new NotificationEventConsumer(codec, notificationProcessor, processedEvents);
        assertThrows(InvalidEventException.class, () -> consumer.onEvent(record("{")));
        verifyNoInteractions(notificationProcessor);
    }

    @Test
    @DisplayName("DLT: guarda el mensaje con su origen y causa decodificados de los headers")
    void deadLetterPersiste() {
        DeadLetterConsumer consumer = new DeadLetterConsumer(failedEvents);
        ConsumerRecord<String, String> dlt = new ConsumerRecord<>("audit-events.DLT", 2, 0L, "simulador", "{malo");
        dlt.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, "audit-events".getBytes(StandardCharsets.UTF_8));
        dlt.headers().add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(4).putInt(2).array());
        dlt.headers().add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(41L).array());
        dlt.headers().add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "sigc-audit".getBytes(StandardCharsets.UTF_8));
        dlt.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN,
                "org.springframework.kafka.listener.ListenerExecutionFailedException".getBytes(StandardCharsets.UTF_8));
        dlt.headers().add(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN,
                InvalidEventException.class.getName().getBytes(StandardCharsets.UTF_8));
        dlt.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "JSON inválido".getBytes(StandardCharsets.UTF_8));

        consumer.onDeadLetter(dlt);

        ArgumentCaptor<FailedEvent> captor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEvents).save(captor.capture());
        FailedEvent f = captor.getValue();
        assertEquals("audit-events", f.getOriginalTopic());
        assertEquals(2, f.getOriginalPartition());
        assertEquals(41L, f.getOriginalOffset());
        assertEquals("sigc-audit", f.getConsumerGroup());
        assertEquals(InvalidEventException.class.getName(), f.getExceptionClass());
        assertEquals("JSON inválido", f.getErrorMessage());
        assertEquals("{malo", f.getPayload());
        assertEquals("simulador", f.getEventKey());
    }
}
