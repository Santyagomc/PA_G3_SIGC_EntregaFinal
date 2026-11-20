package com.backend.sigcapi.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.enums.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Lote ordenado por id: preserva el orden de publicación de los eventos de una misma PQR. */
    List<OutboxEvent> findTop50ByStatusOrderByIdAsc(OutboxStatus status);

    Optional<OutboxEvent> findByEventId(String eventId);

    Page<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);

    Page<OutboxEvent> findByAggregateId(Long aggregateId, Pageable pageable);

    long countByStatus(OutboxStatus status);

    long countByAggregateIdAndStatus(Long aggregateId, OutboxStatus status);
}
