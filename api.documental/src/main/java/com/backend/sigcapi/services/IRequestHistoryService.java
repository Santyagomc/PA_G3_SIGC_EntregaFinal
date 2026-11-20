package com.backend.sigcapi.services;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.backend.sigcapi.entities.RequestHistory;

/**
 * Consulta del historial de auditoría. La ESCRITURA ya no se hace aquí: desde la Entrega 2 (P2)
 * la realiza el consumidor Kafka {@code AuditEventConsumer} a partir del tópico audit-events.
 */
public interface IRequestHistoryService {

    Page<RequestHistory> getHistoryByRequestId(Long idRequest, LocalDate from, LocalDate to, Pageable pageable);

    void deleteByRequestId(Long idRequest);
}
