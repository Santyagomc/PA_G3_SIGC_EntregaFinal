package com.backend.sigcapi.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.backend.sigcapi.enums.RequestEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Registro de idempotencia: un evento (eventId) se procesa como máximo UNA vez por consumidor.
 * La restricción única (event_id, consumer) es la segunda barrera ante duplicados concurrentes.
 */
@Entity
@Table(name = "processed_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_event_consumer", columnNames = { "event_id", "consumer" })
})
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(length = 60, nullable = false)
    private String consumer;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40, nullable = false)
    private RequestEventType eventType;

    @Column(name = "request_id")
    private Long requestId;

    /** Resultado del procesamiento: PROCESADO u OMITIDO (no requería acción). */
    @Column(length = 20, nullable = false)
    private String outcome;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String consumer, RequestEventType eventType, Long requestId, String outcome) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.eventType = eventType;
        this.requestId = requestId;
        this.outcome = outcome;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getConsumer() { return consumer; }
    public void setConsumer(String consumer) { this.consumer = consumer; }
    public RequestEventType getEventType() { return eventType; }
    public void setEventType(RequestEventType eventType) { this.eventType = eventType; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
