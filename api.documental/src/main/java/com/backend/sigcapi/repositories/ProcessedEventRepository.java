package com.backend.sigcapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByEventIdAndConsumer(String eventId, String consumer);

    long countByConsumer(String consumer);

    long countByEventIdAndConsumer(String eventId, String consumer);

    Page<ProcessedEvent> findByConsumer(String consumer, Pageable pageable);
}
