package com.backend.sigcapi.services;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.RequestStatus;

/**
 * Puerto de salida de eventos de dominio de las PQR. La capa de servicios no conoce Kafka:
 * solo declara qué ocurrió. La implementación garantiza la publicación (outbox + relay).
 */
public interface IRequestEventPublisher {

    OutboxEvent publishCreated(Request request, User actor);

    OutboxEvent publishStatusChanged(Request request, RequestStatus oldStatus, RequestStatus newStatus,
            User actor, String observations);
}
