package com.backend.sigcapi.services.implement;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.exceptions.BusinessRuleException;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.repositories.DocumentRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.backend.sigcapi.repositories.specs.RequestSpecifications;
import com.backend.sigcapi.services.IRequestEventPublisher;
import com.backend.sigcapi.services.IRequestHistoryService;
import com.backend.sigcapi.services.IRequestService;
import com.backend.sigcapi.services.IUserService;

/**
 * Casos de uso de las PQR. Entrega 2 (P2): la auditoría (request_history) y la notificación
 * por correo dejaron de ejecutarse dentro de la petición HTTP. Ahora el servicio solo confirma
 * el cambio de negocio y registra el evento de dominio (outbox) en la MISMA transacción; los
 * consumidores Kafka hacen el resto de forma asíncrona.
 */
@Service
public class RequestServiceImpl implements IRequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestServiceImpl.class);

    private final RequestRepository repository;
    private final IRequestHistoryService requestHistoryService;
    private final IUserService userService;
    private final IRequestEventPublisher eventPublisher;
    private final DocumentRepository documentRepository;

    public RequestServiceImpl(RequestRepository repository,
            IRequestHistoryService requestHistoryService,
            IUserService userService,
            IRequestEventPublisher eventPublisher,
            DocumentRepository documentRepository) {
        this.repository = repository;
        this.requestHistoryService = requestHistoryService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.documentRepository = documentRepository;
    }

    @Override
    public Page<Request> all(String search, RequestStatus status, RequesType type,
            LocalDate from, LocalDate to, Pageable pageable) {

        Specification<Request> spec = Specification
                .where(RequestSpecifications.hasSearchText(search))
                .and(RequestSpecifications.hasStatus(status))
                .and(RequestSpecifications.hasType(type))
                .and(RequestSpecifications.createdBetween(from, to));

        return repository.findAll(spec, pageable);
    }

    @Override
    public Request findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la solicitud con ID: " + id));
    }

    @Override
    @Transactional
    public Request save(RequestDTO requestDto) {

        Request request = new Request();
        request.setTitle(requestDto.title());
        request.setDescription(requestDto.description());
        request.setFullName(requestDto.fullName());
        request.setEmail(requestDto.email());
        request.setDocument(requestDto.document());
        request.setCity(requestDto.city());
        request.setPhone(requestDto.phone());
        request.setType(requestDto.type());
        request.setStatus(RequestStatus.CREADO);
        request.setAcceptedDataPolicy(requestDto.acceptedDataPolicy());
        request.setMailStatus(false);
        request.setExpirationDate(requestDto.expirationDate());

        Request saved = repository.save(request);

        User actor = userService.getAuthenticatedUser();
        eventPublisher.publishCreated(saved, actor);
        log.info("PQR radicada id={} tipo={} por documento={}", saved.getId(), saved.getType(), actor.getDocument());

        return saved;
    }

    @Override
    @Transactional
    public Request updateStatus(Long id, RequestStatus newStatus, String observations) {
        Request request = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));

        RequestStatus oldStatus = request.getStatus();
        if (oldStatus == newStatus) {
            throw new BusinessRuleException("La solicitud " + id + " ya se encuentra en estado " + newStatus
                    + "; no hay transición que registrar.");
        }

        request.setStatus(newStatus);
        Request updated = repository.save(request);

        User currentUser = userService.getAuthenticatedUser();
        eventPublisher.publishStatusChanged(updated, oldStatus, newStatus, currentUser, observations);
        log.info("PQR id={} cambió de estado {} -> {} por documento={}", id, oldStatus, newStatus,
                currentUser.getDocument());

        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Request request = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la solicitud con ID: " + id));
        // Dependencias con FK hacia requests: historial (generado por el consumidor) y documentos.
        requestHistoryService.deleteByRequestId(id);
        documentRepository.deleteAll(documentRepository.findByRequestId(id));
        repository.delete(request);
    }

}
