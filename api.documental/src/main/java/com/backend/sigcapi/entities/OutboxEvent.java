package com.backend.sigcapi.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.enums.RequestEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Patrón Transactional Outbox: el evento se guarda en la MISMA transacción que el cambio
 * de negocio y un relay lo publica después en Kafka. Así no hay eventos huérfanos
 * (transacción revertida) ni eventos perdidos (Kafka caído en el momento del commit).
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false, unique = true)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40, nullable = false)
    private RequestEventType eventType;

    /** Identificador de la PQR; también es la clave (key) del mensaje Kafka. */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(length = 120, nullable = false)
    private String topic;

    @Column(name = "event_key", length = 64, nullable = false)
    private String eventKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Identificador de correlación de la petición HTTP que originó el evento (observabilidad). */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    public void markPublished(int partition, long offset) {
        this.status = OutboxStatus.PUBLISHED;
        this.kafkaPartition = partition;
        this.kafkaOffset = offset;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
    }

    /**
     * Registra un intento fallido. Devuelve {@code true} si con este intento se agotaron
     * los reintentos y el evento pasó a FAILED.
     */
    public boolean registerFailure(String error, int maxAttempts) {
        this.attempts = (this.attempts == null ? 0 : this.attempts) + 1;
        this.lastError = error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error);
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
            return true;
        }
        return false;
    }

    public void resetForRetry() {
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.lastError = null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public RequestEventType getEventType() { return eventType; }
    public void setEventType(RequestEventType eventType) { this.eventType = eventType; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Integer getKafkaPartition() { return kafkaPartition; }
    public void setKafkaPartition(Integer kafkaPartition) { this.kafkaPartition = kafkaPartition; }
    public Long getKafkaOffset() { return kafkaOffset; }
    public void setKafkaOffset(Long kafkaOffset) { this.kafkaOffset = kafkaOffset; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
