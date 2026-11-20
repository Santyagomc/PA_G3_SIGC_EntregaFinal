package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.enums.RequestEventType;
import com.backend.sigcapi.messaging.event.EventHeaders;
import com.backend.sigcapi.messaging.producer.KafkaEventSender;
import com.backend.sigcapi.messaging.producer.OutboxRelay;
import com.backend.sigcapi.repositories.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository repository;
    @Mock private KafkaEventSender sender;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, sender, 3);
    }

    private OutboxEvent pending(long id, long requestId) {
        OutboxEvent e = new OutboxEvent();
        e.setId(id);
        e.setEventId("00000000-0000-4000-8000-00000000000" + id);
        e.setEventType(RequestEventType.REQUEST_STATUS_CHANGED);
        e.setAggregateId(requestId);
        e.setEventKey(String.valueOf(requestId));
        e.setTopic("audit-events");
        e.setPayload("{}");
        e.setStatus(OutboxStatus.PENDING);
        e.setAttempts(0);
        return e;
    }

    @Test
    @DisplayName("publica los pendientes, guarda partición/offset y los marca PUBLISHED")
    @SuppressWarnings("unchecked")
    void publicaPendientes() throws Exception {
        OutboxEvent e1 = pending(1, 10);
        OutboxEvent e2 = pending(2, 11);
        when(repository.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)).thenReturn(List.of(e1, e2));
        when(sender.send(eq("audit-events"), anyString(), anyString(), anyMap()))
                .thenReturn(new KafkaEventSender.Delivery("audit-events", 1, 7L))
                .thenReturn(new KafkaEventSender.Delivery("audit-events", 2, 8L));

        relay.publishPending();

        assertEquals(OutboxStatus.PUBLISHED, e1.getStatus());
        assertEquals(1, e1.getKafkaPartition());
        assertEquals(7L, e1.getKafkaOffset());
        assertNotNull(e1.getPublishedAt());
        assertEquals(OutboxStatus.PUBLISHED, e2.getStatus());

        // la clave del mensaje es el requestId y viajan los headers del evento
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(sender).send(eq("audit-events"), eq("10"), eq("{}"), headers.capture());
        assertEquals(e1.getEventId(), headers.getValue().get(EventHeaders.EVENT_ID));
        assertEquals("REQUEST_STATUS_CHANGED", headers.getValue().get(EventHeaders.EVENT_TYPE));
    }

    @Test
    @DisplayName("si Kafka falla: registra el intento, conserva PENDING y DETIENE el lote (orden)")
    void falloDetieneElLote() throws Exception {
        OutboxEvent e1 = pending(1, 10);
        OutboxEvent e2 = pending(2, 10);
        when(repository.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)).thenReturn(List.of(e1, e2));
        when(sender.send(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new ExecutionException(new KafkaException("broker no disponible")));

        relay.publishPending();

        assertEquals(OutboxStatus.PENDING, e1.getStatus());
        assertEquals(1, e1.getAttempts());
        assertTrue(e1.getLastError().contains("broker no disponible"));
        verify(sender, times(1)).send(anyString(), anyString(), anyString(), anyMap()); // e2 no se intentó
        assertEquals(0, e2.getAttempts());
    }

    @Test
    @DisplayName("al agotar max-attempts el evento pasa a FAILED")
    void agotaReintentos() throws Exception {
        OutboxEvent e1 = pending(1, 10);
        e1.setAttempts(2);
        when(repository.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)).thenReturn(List.of(e1));
        when(sender.send(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new KafkaException("timeout"));

        relay.publishPending();

        assertEquals(OutboxStatus.FAILED, e1.getStatus());
        assertEquals(3, e1.getAttempts());
        verify(repository).save(e1);
    }

    @Test
    @DisplayName("sin pendientes no contacta a Kafka")
    void sinPendientes() throws Exception {
        when(repository.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)).thenReturn(List.of());
        relay.publishPending();
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("resetForRetry devuelve un evento FAILED a PENDING con contadores en cero")
    void resetForRetry() {
        OutboxEvent e = pending(1, 10);
        e.registerFailure("x", 1);
        assertEquals(OutboxStatus.FAILED, e.getStatus());
        e.resetForRetry();
        assertEquals(OutboxStatus.PENDING, e.getStatus());
        assertEquals(0, e.getAttempts());
        assertNull(e.getLastError());
    }
}
