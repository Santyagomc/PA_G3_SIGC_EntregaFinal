package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.sigcapi.entities.ProcessedEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.RequestHistory;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.consumer.AuditEventProcessor;
import com.backend.sigcapi.messaging.consumer.ConsumerNames;
import com.backend.sigcapi.messaging.event.NonRetryableEventException;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.ProcessedEventRepository;
import com.backend.sigcapi.repositories.RequestHistoryRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.backend.sigcapi.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuditEventProcessorTest {

    @Mock private ProcessedEventRepository processedEvents;
    @Mock private RequestHistoryRepository historyRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AuditEventProcessor processor;

    private final RequestEvent cambio = RequestEvent.statusChanged(5L, RequesType.QUEJA, RequestStatus.CREADO,
            RequestStatus.CERRADO, "1000000001", 1L, "Resuelto");

    @Test
    @DisplayName("evento nuevo: guarda el historial con los datos del evento y marca la idempotencia")
    void eventoNuevo() {
        Request request = new Request();
        request.setId(5L);
        User actor = new User();
        actor.setId(1L);
        when(processedEvents.existsByEventIdAndConsumer(cambio.eventId(), ConsumerNames.AUDIT)).thenReturn(false);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertTrue(processor.process(cambio));

        ArgumentCaptor<RequestHistory> history = ArgumentCaptor.forClass(RequestHistory.class);
        verify(historyRepository).save(history.capture());
        assertSame(request, history.getValue().getRequest());
        assertSame(actor, history.getValue().getUser());
        assertEquals(RequestStatus.CREADO, history.getValue().getStatusOld());
        assertEquals(RequestStatus.CERRADO, history.getValue().getStatusNew());
        assertEquals(cambio.eventId(), history.getValue().getEventId());
        assertEquals("Resuelto", history.getValue().getObservations());
        assertNotNull(history.getValue().getChangedAt());

        ArgumentCaptor<ProcessedEvent> processed = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEvents).save(processed.capture());
        assertEquals(ConsumerNames.AUDIT, processed.getValue().getConsumer());
        assertEquals(cambio.eventId(), processed.getValue().getEventId());
    }

    @Test
    @DisplayName("evento duplicado: no vuelve a escribir historial (idempotencia)")
    void duplicado() {
        when(processedEvents.existsByEventIdAndConsumer(cambio.eventId(), ConsumerNames.AUDIT)).thenReturn(true);

        assertFalse(processor.process(cambio));

        verifyNoInteractions(historyRepository, requestRepository, userRepository);
        verify(processedEvents, never()).save(any());
    }

    @Test
    @DisplayName("PQR inexistente: NonRetryableEventException (va al DLT sin reintentos)")
    void pqrInexistente() {
        when(processedEvents.existsByEventIdAndConsumer(cambio.eventId(), ConsumerNames.AUDIT)).thenReturn(false);
        when(requestRepository.findById(5L)).thenReturn(Optional.empty());

        NonRetryableEventException ex = assertThrows(NonRetryableEventException.class, () -> processor.process(cambio));
        assertTrue(ex.getMessage().contains("5"));
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("actor inexistente: NonRetryableEventException")
    void actorInexistente() {
        when(processedEvents.existsByEventIdAndConsumer(cambio.eventId(), ConsumerNames.AUDIT)).thenReturn(false);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(new Request()));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NonRetryableEventException.class, () -> processor.process(cambio));
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQUEST_CREATED: el historial queda con estado anterior nulo (radicación)")
    void radicacion() {
        RequestEvent creado = RequestEvent.created(5L, RequesType.QUEJA, RequestStatus.CREADO, "1000000001", 1L);
        when(processedEvents.existsByEventIdAndConsumer(creado.eventId(), ConsumerNames.AUDIT)).thenReturn(false);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(new Request()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

        processor.process(creado);

        ArgumentCaptor<RequestHistory> history = ArgumentCaptor.forClass(RequestHistory.class);
        verify(historyRepository).save(history.capture());
        assertNull(history.getValue().getStatusOld());
        assertEquals(RequestStatus.CREADO, history.getValue().getStatusNew());
    }
}
