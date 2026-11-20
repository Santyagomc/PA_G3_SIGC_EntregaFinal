package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import com.backend.sigcapi.entities.Notification;
import com.backend.sigcapi.entities.ProcessedEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.enums.NotificationStatus;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.consumer.ConsumerNames;
import com.backend.sigcapi.messaging.consumer.NotificationProcessor;
import com.backend.sigcapi.messaging.consumer.NotificationSender;
import com.backend.sigcapi.messaging.event.NonRetryableEventException;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.NotificationRepository;
import com.backend.sigcapi.repositories.ProcessedEventRepository;
import com.backend.sigcapi.repositories.RequestRepository;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock private ProcessedEventRepository processedEvents;
    @Mock private NotificationRepository notifications;
    @Mock private RequestRepository requestRepository;
    @Mock private NotificationSender sender;
    @InjectMocks private NotificationProcessor processor;

    private Request request(Long id) {
        Request r = new Request();
        r.setId(id);
        r.setTitle("Queja por atención");
        r.setFullName("Santiago Murcia");
        r.setEmail("ciudadano@example.com");
        r.setType(RequesType.QUEJA);
        r.setMailStatus(false);
        r.setExpirationDate(LocalDate.now().plusDays(15));
        return r;
    }

    private RequestEvent cambio(RequestStatus from, RequestStatus to) {
        return RequestEvent.statusChanged(9L, RequesType.QUEJA, from, to, "1000000001", 1L, "Observación");
    }

    @Test
    @DisplayName("cambio a CERRADO: notifica, guarda la notificación y marca mail_status = true")
    void cerradoNotifica() {
        RequestEvent event = cambio(RequestStatus.CREADO, RequestStatus.CERRADO);
        Request r = request(9L);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(false);
        when(requestRepository.findById(9L)).thenReturn(Optional.of(r));
        when(sender.send(eq("ciudadano@example.com"), anyString(), anyString())).thenReturn(NotificationStatus.SIMULATED);

        assertTrue(processor.process(event));

        ArgumentCaptor<Notification> n = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(n.capture());
        assertEquals(event.eventId(), n.getValue().getEventId());
        assertEquals(NotificationStatus.SIMULATED, n.getValue().getStatus());
        assertTrue(n.getValue().getSubject().contains("Actualización de Estado"));
        assertTrue(n.getValue().getBody().contains("Estado Actual: CERRADO"));
        assertTrue(n.getValue().getBody().contains("Observaciones: Observación"));
        assertTrue(r.getMailStatus());
        verify(requestRepository).save(r);

        ArgumentCaptor<ProcessedEvent> p = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEvents).save(p.capture());
        assertEquals(ConsumerNames.OUTCOME_PROCESSED, p.getValue().getOutcome());
    }

    @Test
    @DisplayName("cambio a FALTA_DOCUMENTACION: también notifica")
    void faltaDocumentacionNotifica() {
        RequestEvent event = cambio(RequestStatus.CREADO, RequestStatus.FALTA_DOCUMENTACION);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(false);
        when(requestRepository.findById(9L)).thenReturn(Optional.of(request(9L)));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(NotificationStatus.SENT);

        assertTrue(processor.process(event));
        verify(notifications).save(any(Notification.class));
    }

    @Test
    @DisplayName("REQUEST_CREATED: envía acuse de radicación sin modificar mail_status")
    void acuseDeRadicacion() {
        RequestEvent event = RequestEvent.created(9L, RequesType.QUEJA, RequestStatus.CREADO, "1075000001", 2L);
        Request r = request(9L);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(false);
        when(requestRepository.findById(9L)).thenReturn(Optional.of(r));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(NotificationStatus.SIMULATED);

        assertTrue(processor.process(event));

        ArgumentCaptor<Notification> n = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(n.capture());
        assertTrue(n.getValue().getSubject().startsWith("Radicación recibida"));
        assertTrue(n.getValue().getBody().contains("Fecha límite de respuesta"));
        assertFalse(r.getMailStatus());
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("reapertura desde CERRADO: no notifica y queda OMITIDO")
    void desdeCerradoSeOmite() {
        RequestEvent event = cambio(RequestStatus.CERRADO, RequestStatus.CREADO);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(false);

        assertFalse(processor.process(event));

        verifyNoInteractions(sender, notifications, requestRepository);
        ArgumentCaptor<ProcessedEvent> p = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEvents).save(p.capture());
        assertEquals(ConsumerNames.OUTCOME_SKIPPED, p.getValue().getOutcome());
    }

    @Test
    @DisplayName("duplicado: no se envía un segundo correo")
    void duplicadoNoReenvia() {
        RequestEvent event = cambio(RequestStatus.CREADO, RequestStatus.CERRADO);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(true);

        assertFalse(processor.process(event));
        verifyNoInteractions(sender, notifications, requestRepository);
    }

    @Test
    @DisplayName("fallo SMTP: la excepción se propaga (el contenedor reintenta) y no se marca procesado")
    void falloSmtpSePropaga() {
        RequestEvent event = cambio(RequestStatus.CREADO, RequestStatus.CERRADO);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(false);
        when(requestRepository.findById(9L)).thenReturn(Optional.of(request(9L)));
        when(sender.send(anyString(), anyString(), anyString())).thenThrow(new MailSendException("SMTP caído"));

        assertThrows(MailSendException.class, () -> processor.process(event));
        verify(processedEvents, never()).save(any());
        verify(notifications, never()).save(any());
    }

    @Test
    @DisplayName("PQR inexistente: NonRetryableEventException")
    void pqrInexistente() {
        RequestEvent event = cambio(RequestStatus.CREADO, RequestStatus.CERRADO);
        when(processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)).thenReturn(false);
        when(requestRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NonRetryableEventException.class, () -> processor.process(event));
    }
}
