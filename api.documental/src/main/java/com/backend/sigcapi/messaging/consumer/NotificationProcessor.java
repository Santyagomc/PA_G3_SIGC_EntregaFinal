package com.backend.sigcapi.messaging.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.sigcapi.entities.Notification;
import com.backend.sigcapi.entities.ProcessedEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.enums.NotificationStatus;
import com.backend.sigcapi.enums.RequestEventType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.event.NonRetryableEventException;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.NotificationRepository;
import com.backend.sigcapi.repositories.ProcessedEventRepository;
import com.backend.sigcapi.repositories.RequestRepository;

/**
 * Lógica del consumidor de notificaciones al ciudadano.
 *
 * <ul>
 *   <li>REQUEST_CREATED → acuse de radicación (Ley 1755 de 2015: informar el número de radicado).</li>
 *   <li>REQUEST_STATUS_CHANGED a CERRADO o FALTA_DOCUMENTACION (si no estaba CERRADO) → aviso de
 *       actualización y {@code requests.mail_status = true}. Es la misma regla que antes se
 *       ejecutaba de forma síncrona dentro de la petición HTTP.</li>
 *   <li>Otros cambios → se marcan OMITIDO (no requieren notificación).</li>
 * </ul>
 */
@Service
public class NotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final NotificationRepository notifications;
    private final RequestRepository requestRepository;
    private final NotificationSender sender;

    public NotificationProcessor(ProcessedEventRepository processedEvents, NotificationRepository notifications,
            RequestRepository requestRepository, NotificationSender sender) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.requestRepository = requestRepository;
        this.sender = sender;
    }

    /** @return {@code true} si se generó una notificación. */
    @Transactional
    public boolean process(RequestEvent event) {
        if (processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.NOTIFICATIONS)) {
            log.info("[NOTIFICACIONES] Evento duplicado ignorado eventId={} (ya procesado por {})",
                    event.eventId(), ConsumerNames.NOTIFICATIONS);
            return false;
        }

        if (!requiresNotification(event)) {
            processedEvents.save(new ProcessedEvent(event.eventId(), ConsumerNames.NOTIFICATIONS, event.eventType(),
                    event.requestId(), ConsumerNames.OUTCOME_SKIPPED));
            log.info("[NOTIFICACIONES] {} -> {} no requiere notificación (OMITIDO) requestId={}",
                    event.estadoAnterior(), event.estadoNuevo(), event.requestId());
            return false;
        }

        Request request = requestRepository.findById(event.requestId())
                .orElseThrow(() -> new NonRetryableEventException(
                        "La PQR " + event.requestId() + " no existe; no hay a quién notificar"));

        String subject = subject(event, request);
        String body = body(event, request);
        NotificationStatus status = sender.send(request.getEmail(), subject, body);

        Notification notification = new Notification();
        notification.setEventId(event.eventId());
        notification.setRequestId(request.getId());
        notification.setRecipient(request.getEmail());
        notification.setSubject(subject);
        notification.setBody(body);
        notification.setStatus(status);
        notifications.save(notification);

        if (event.eventType() == RequestEventType.REQUEST_STATUS_CHANGED) {
            request.setMailStatus(true);
            requestRepository.save(request);
        }

        processedEvents.save(new ProcessedEvent(event.eventId(), ConsumerNames.NOTIFICATIONS, event.eventType(),
                event.requestId(), ConsumerNames.OUTCOME_PROCESSED));
        log.info("[NOTIFICACIONES] Notificación {} registrada requestId={} tipo={}", status, request.getId(),
                event.eventType());
        return true;
    }

    static boolean requiresNotification(RequestEvent event) {
        if (event.eventType() == RequestEventType.REQUEST_CREATED) {
            return true;
        }
        return event.estadoAnterior() != RequestStatus.CERRADO
                && (event.estadoNuevo() == RequestStatus.CERRADO
                        || event.estadoNuevo() == RequestStatus.FALTA_DOCUMENTACION);
    }

    private static String subject(RequestEvent event, Request request) {
        if (event.eventType() == RequestEventType.REQUEST_CREATED) {
            return "Radicación recibida - PQR No. " + request.getId() + " - SIGCAPI";
        }
        return "Actualización de Estado - PQR No. " + request.getId() + " - SIGCAPI";
    }

    private static String body(RequestEvent event, Request request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hola ").append(request.getFullName() == null ? "" : request.getFullName()).append(",\n\n");
        if (event.eventType() == RequestEventType.REQUEST_CREATED) {
            sb.append("Tu solicitud fue radicada correctamente en SIGCAPI.\n\n");
        } else {
            sb.append("Se ha actualizado el estado de tu solicitud en SIGCAPI.\n\n");
        }
        sb.append("------------------------------------------\n");
        sb.append("Número de Radicado: ").append(request.getId()).append('\n');
        sb.append("Título: ").append(request.getTitle()).append('\n');
        sb.append("Tipo: ").append(request.getType()).append('\n');
        if (event.estadoAnterior() != null) {
            sb.append("Estado Anterior: ").append(event.estadoAnterior()).append('\n');
        }
        sb.append("Estado Actual: ").append(event.estadoNuevo()).append('\n');
        if (event.observaciones() != null && !event.observaciones().isBlank()) {
            sb.append("Observaciones: ").append(event.observaciones()).append('\n');
        }
        if (request.getExpirationDate() != null && event.eventType() == RequestEventType.REQUEST_CREATED) {
            sb.append("Fecha límite de respuesta: ").append(request.getExpirationDate()).append('\n');
        }
        sb.append("------------------------------------------\n\n");
        sb.append("Este es un correo automático, por favor no lo respondas.");
        return sb.toString();
    }
}
