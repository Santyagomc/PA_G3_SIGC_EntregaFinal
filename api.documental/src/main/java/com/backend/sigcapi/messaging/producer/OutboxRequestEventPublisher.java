package com.backend.sigcapi.messaging.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.messaging.event.EventCodec;
import com.backend.sigcapi.messaging.event.RequestEvent;
import com.backend.sigcapi.repositories.OutboxEventRepository;
import com.backend.sigcapi.services.IRequestEventPublisher;

/**
 * Productor (fase 1 de 2): registra el evento en la tabla outbox dentro de la transacción del
 * caso de uso. {@code Propagation.MANDATORY} hace explícito el requisito: si no hay transacción
 * activa, falla, porque sin ella no se garantiza la atomicidad "cambio de negocio + evento".
 * La fase 2 (envío real a Kafka) la hace {@link OutboxRelay} después del commit.
 */
@Service
public class OutboxRequestEventPublisher implements IRequestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxRequestEventPublisher.class);

    private final OutboxEventRepository repository;
    private final EventCodec codec;
    private final String topic;

    public OutboxRequestEventPublisher(OutboxEventRepository repository, EventCodec codec,
            @Value("${app.kafka.topics.request-events:audit-events}") String topic) {
        this.repository = repository;
        this.codec = codec;
        this.topic = topic;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishCreated(Request request, User actor) {
        RequestEvent event = RequestEvent.created(request.getId(), request.getType(), request.getStatus(),
                actor.getDocument(), actor.getId());
        return record(event);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publishStatusChanged(Request request, RequestStatus oldStatus, RequestStatus newStatus,
            User actor, String observations) {
        RequestEvent event = RequestEvent.statusChanged(request.getId(), request.getType(), oldStatus, newStatus,
                actor.getDocument(), actor.getId(), truncate(observations, 500));
        return record(event);
    }

    private OutboxEvent record(RequestEvent event) {
        codec.validate(event); // nunca se publica un evento que viole su propio contrato

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(event.eventId());
        outbox.setEventType(event.eventType());
        outbox.setAggregateId(event.requestId());
        outbox.setTopic(topic);
        outbox.setEventKey(String.valueOf(event.requestId()));
        outbox.setPayload(codec.toJson(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setCorrelationId(org.slf4j.MDC.get("correlationId"));

        OutboxEvent saved = repository.save(outbox);
        log.info("[OUTBOX] Evento registrado eventId={} type={} requestId={} topic={} (pendiente de publicar tras el commit)",
                event.eventId(), event.eventType(), event.requestId(), topic);
        return saved;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
