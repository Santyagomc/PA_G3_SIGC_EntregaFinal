package com.backend.sigcapi.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.dto.PageResponseDTO;
import com.backend.sigcapi.entities.FailedEvent;
import com.backend.sigcapi.entities.Notification;
import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.entities.ProcessedEvent;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.exceptions.BusinessRuleException;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.messaging.consumer.ConsumerNames;
import com.backend.sigcapi.messaging.producer.EventSimulator;
import com.backend.sigcapi.repositories.FailedEventRepository;
import com.backend.sigcapi.repositories.NotificationRepository;
import com.backend.sigcapi.repositories.OutboxEventRepository;
import com.backend.sigcapi.repositories.ProcessedEventRepository;
import com.backend.sigcapi.repositories.RequestHistoryRepository;
import com.backend.sigcapi.services.IUserService;
import com.backend.sigcapi.util.ApiResponse;

/**
 * Observabilidad y simulación del flujo orientado a eventos (solo ADMIN).
 * Permite verificar, desde la API, cada etapa: outbox (productor) → consumidores → DLT.
 */
@RestController
@RequestMapping("/events")
@PreAuthorize("hasRole('ADMIN')")
public class EventController {

    private final OutboxEventRepository outboxRepository;
    private final ProcessedEventRepository processedRepository;
    private final NotificationRepository notificationRepository;
    private final FailedEventRepository failedRepository;
    private final RequestHistoryRepository historyRepository;
    private final EventSimulator simulator;
    private final IUserService userService;

    public EventController(OutboxEventRepository outboxRepository, ProcessedEventRepository processedRepository,
            NotificationRepository notificationRepository, FailedEventRepository failedRepository,
            RequestHistoryRepository historyRepository, EventSimulator simulator, IUserService userService) {
        this.outboxRepository = outboxRepository;
        this.processedRepository = processedRepository;
        this.notificationRepository = notificationRepository;
        this.failedRepository = failedRepository;
        this.historyRepository = historyRepository;
        this.simulator = simulator;
        this.userService = userService;
    }

    // ---------------------------------------------------------------- monitoreo

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary() {
        Map<String, Object> outbox = new LinkedHashMap<>();
        for (OutboxStatus status : OutboxStatus.values()) {
            outbox.put(status.name(), outboxRepository.countByStatus(status));
        }
        Map<String, Object> consumers = new LinkedHashMap<>();
        consumers.put(ConsumerNames.AUDIT, processedRepository.countByConsumer(ConsumerNames.AUDIT));
        consumers.put(ConsumerNames.NOTIFICATIONS, processedRepository.countByConsumer(ConsumerNames.NOTIFICATIONS));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("outbox", outbox);
        data.put("eventosProcesadosPorConsumidor", consumers);
        data.put("registrosHistorial", historyRepository.count());
        data.put("notificaciones", notificationRepository.count());
        data.put("mensajesEnDLT", failedRepository.count());
        return ResponseEntity.ok(new ApiResponse<>(true, "Resumen del flujo de eventos.", data));
    }

    @GetMapping("/outbox")
    public ResponseEntity<ApiResponse<PageResponseDTO<OutboxEvent>>> outbox(
            @RequestParam(name = "status", required = false) OutboxStatus status,
            @RequestParam(name = "requestId", required = false) Long requestId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size, "id");
        Page<OutboxEvent> result;
        if (requestId != null) {
            result = outboxRepository.findByAggregateId(requestId, pageable);
        } else if (status != null) {
            result = outboxRepository.findByStatus(status, pageable);
        } else {
            result = outboxRepository.findAll(pageable);
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Eventos del outbox (productor).", toPage(result)));
    }

    @PostMapping("/outbox/{id}/retry")
    public ResponseEntity<ApiResponse<OutboxEvent>> retry(@PathVariable Long id) {
        OutboxEvent event = outboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento de outbox " + id));
        if (event.getStatus() != OutboxStatus.FAILED) {
            throw new BusinessRuleException("Solo se reintentan eventos FAILED (estado actual: " + event.getStatus() + ")");
        }
        event.resetForRetry();
        return ResponseEntity.ok(new ApiResponse<>(true, "Evento devuelto a PENDING; el relay lo publicará.",
                outboxRepository.save(event)));
    }

    @GetMapping("/processed")
    public ResponseEntity<ApiResponse<PageResponseDTO<ProcessedEvent>>> processed(
            @RequestParam(name = "consumer", required = false) String consumer,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size, "id");
        Page<ProcessedEvent> result = consumer == null || consumer.isBlank()
                ? processedRepository.findAll(pageable)
                : processedRepository.findByConsumer(consumer, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Eventos procesados por los consumidores.", toPage(result)));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<PageResponseDTO<Notification>>> notifications(
            @RequestParam(name = "requestId", required = false) Long requestId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size, "id");
        Page<Notification> result = requestId == null
                ? notificationRepository.findAll(pageable)
                : notificationRepository.findByRequestId(requestId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Notificaciones generadas por eventos.", toPage(result)));
    }

    @GetMapping("/dead-letters")
    public ResponseEntity<ApiResponse<PageResponseDTO<FailedEvent>>> deadLetters(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<FailedEvent> result = failedRepository.findAll(pageable(page, size, "id"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Mensajes recibidos en el Dead Letter Topic.", toPage(result)));
    }

    // ---------------------------------------------------------------- simulador

    @PostMapping("/simulate/duplicate/{eventId}")
    public ResponseEntity<ApiResponse<EventSimulator.SimulationResult>> simulateDuplicate(@PathVariable String eventId) {
        return ResponseEntity.accepted().body(new ApiResponse<>(true, "Duplicado publicado.", simulator.duplicate(eventId)));
    }

    @PostMapping("/simulate/invalid-payload")
    public ResponseEntity<ApiResponse<EventSimulator.SimulationResult>> simulateInvalidPayload() {
        return ResponseEntity.accepted().body(new ApiResponse<>(true, "Mensaje con JSON inválido publicado.",
                simulator.invalidPayload()));
    }

    @PostMapping("/simulate/contract-violation")
    public ResponseEntity<ApiResponse<EventSimulator.SimulationResult>> simulateContractViolation() {
        return ResponseEntity.accepted().body(new ApiResponse<>(true, "Mensaje que viola el contrato publicado.",
                simulator.contractViolation()));
    }

    @PostMapping("/simulate/unknown-request")
    public ResponseEntity<ApiResponse<EventSimulator.SimulationResult>> simulateUnknownRequest() {
        Long actorId = userService.getAuthenticatedUser().getId();
        return ResponseEntity.accepted().body(new ApiResponse<>(true, "Evento de PQR inexistente publicado.",
                simulator.unknownRequest(actorId)));
    }

    // ---------------------------------------------------------------- utilidades

    private static Pageable pageable(int page, int size, String sortField) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(sortField).descending());
    }

    private static <T> PageResponseDTO<T> toPage(Page<T> page) {
        return new PageResponseDTO<>(page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber());
    }
}
