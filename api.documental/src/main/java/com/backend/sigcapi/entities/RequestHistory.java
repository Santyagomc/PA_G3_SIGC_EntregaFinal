package com.backend.sigcapi.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.backend.sigcapi.enums.RequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Historial de auditoría de una PQR. Desde la Entrega 2 (P2) lo escribe EXCLUSIVAMENTE el
 * consumidor Kafka {@code AuditEventConsumer} a partir de los eventos del tópico audit-events.
 */
@Entity
@Table(name = "request_history")
public class RequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    /** Nulo para el evento de radicación (no existía estado anterior). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_old")
    private RequestStatus statusOld;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_new", nullable = false)
    private RequestStatus statusNew;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Evento Kafka que originó el registro (único: barrera de idempotencia en BD). */
    @Column(name = "event_id", length = 36, unique = true)
    private String eventId;

    @Column(length = 500)
    private String observations;

    /** Momento en que ocurrió el hecho de negocio (timestamp del evento). */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Momento en que el consumidor lo persistió; la diferencia con changedAt es la latencia asíncrona. */
    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    void prePersist() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Request getRequest() { return request; }
    public void setRequest(Request request) { this.request = request; }
    public RequestStatus getStatusOld() { return statusOld; }
    public void setStatusOld(RequestStatus statusOld) { this.statusOld = statusOld; }
    public RequestStatus getStatusNew() { return statusNew; }
    public void setStatusNew(RequestStatus statusNew) { this.statusNew = statusNew; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
