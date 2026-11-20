package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestEventType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.event.EventCodec;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.messaging.producer.OutboxRequestEventPublisher;
import com.backend.sigcapi.repositories.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxRequestEventPublisherTest {

    @Mock private OutboxEventRepository repository;
    private final EventCodec codec = new EventCodec();
    private OutboxRequestEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxRequestEventPublisher(repository, codec, "audit-events");
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Request request() {
        Request r = new Request();
        r.setId(15L);
        r.setType(RequesType.RECLAMO);
        r.setStatus(RequestStatus.CREADO);
        return r;
    }

    private User actor() {
        User u = new User();
        u.setId(3L);
        u.setDocument("1075000001");
        return u;
    }

    @Test
    @DisplayName("publishCreated: registra en outbox un REQUEST_CREATED PENDING con clave = requestId")
    void created() {
        OutboxEvent out = publisher.publishCreated(request(), actor());

        assertEquals(OutboxStatus.PENDING, out.getStatus());
        assertEquals(RequestEventType.REQUEST_CREATED, out.getEventType());
        assertEquals("audit-events", out.getTopic());
        assertEquals("15", out.getEventKey());
        assertEquals(15L, out.getAggregateId());
        assertEquals(0, out.getAttempts());

        RequestEvent event = codec.fromJson(out.getPayload()); // el payload cumple el contrato
        assertEquals(out.getEventId(), event.eventId());
        assertEquals(RequestStatus.CREADO, event.estadoNuevo());
        assertNull(event.estadoAnterior());
        assertEquals(3L, event.actorId());
        assertEquals("1075000001", event.actor());
    }

    @Test
    @DisplayName("publishStatusChanged: registra la transición con observaciones")
    void statusChanged() {
        OutboxEvent out = publisher.publishStatusChanged(request(), RequestStatus.CREADO,
                RequestStatus.FALTA_DOCUMENTACION, actor(), "Adjunte la cédula");

        RequestEvent event = codec.fromJson(out.getPayload());
        assertEquals(RequestEventType.REQUEST_STATUS_CHANGED, event.eventType());
        assertEquals(RequestStatus.CREADO, event.estadoAnterior());
        assertEquals(RequestStatus.FALTA_DOCUMENTACION, event.estadoNuevo());
        assertEquals("Adjunte la cédula", event.observaciones());
        verify(repository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("cada evento recibe un eventId distinto (clave de idempotencia)")
    void eventIdUnico() {
        OutboxEvent a = publisher.publishCreated(request(), actor());
        OutboxEvent b = publisher.publishCreated(request(), actor());
        assertNotEquals(a.getEventId(), b.getEventId());
    }
}
