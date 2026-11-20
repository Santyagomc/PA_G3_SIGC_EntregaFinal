package com.backend.sigcapi.messaging.consumer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.sigcapi.entities.ProcessedEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.RequestHistory;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.messaging.event.NonRetryableEventException;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.ProcessedEventRepository;
import com.backend.sigcapi.repositories.RequestHistoryRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.backend.sigcapi.repositories.UserRepository;

/**
 * Lógica del consumidor de auditoría: convierte cada evento en una fila de request_history.
 * Todo ocurre en UNA transacción: fila de historial + marca de idempotencia. Si algo falla,
 * no queda ninguna de las dos y el mensaje se reintenta.
 */
@Service
public class AuditEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(AuditEventProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final RequestHistoryRepository historyRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;

    public AuditEventProcessor(ProcessedEventRepository processedEvents, RequestHistoryRepository historyRepository,
            RequestRepository requestRepository, UserRepository userRepository) {
        this.processedEvents = processedEvents;
        this.historyRepository = historyRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    /** @return {@code true} si se persistió el historial; {@code false} si era un duplicado. */
    @Transactional
    public boolean process(RequestEvent event) {
        if (processedEvents.existsByEventIdAndConsumer(event.eventId(), ConsumerNames.AUDIT)) {
            log.info("[AUDITORIA] Evento duplicado ignorado eventId={} (ya procesado por {})",
                    event.eventId(), ConsumerNames.AUDIT);
            return false;
        }

        Request request = requestRepository.findById(event.requestId())
                .orElseThrow(() -> new NonRetryableEventException(
                        "La PQR " + event.requestId() + " no existe; el evento no se puede auditar"));
        User actor = userRepository.findById(event.actorId())
                .orElseThrow(() -> new NonRetryableEventException(
                        "El actor " + event.actorId() + " no existe; el evento no se puede auditar"));

        RequestHistory history = new RequestHistory();
        history.setRequest(request);
        history.setStatusOld(event.estadoAnterior());
        history.setStatusNew(event.estadoNuevo());
        history.setUser(actor);
        history.setEventId(event.eventId());
        history.setObservations(event.observaciones());
        history.setChangedAt(LocalDateTime.ofInstant(Instant.parse(event.timestamp()), ZoneId.systemDefault()));
        historyRepository.save(history);

        processedEvents.save(new ProcessedEvent(event.eventId(), ConsumerNames.AUDIT, event.eventType(),
                event.requestId(), ConsumerNames.OUTCOME_PROCESSED));

        log.info("[AUDITORIA] Historial registrado requestId={} {} -> {} actor={}",
                event.requestId(), event.estadoAnterior() == null ? "(radicación)" : event.estadoAnterior(),
                event.estadoNuevo(), event.actor());
        return true;
    }
}
