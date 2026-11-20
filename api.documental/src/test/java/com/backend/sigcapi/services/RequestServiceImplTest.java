package com.backend.sigcapi.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Document;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.exceptions.BusinessRuleException;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.repositories.DocumentRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.backend.sigcapi.services.implement.RequestServiceImpl;

/**
 * Entrega 2 (P2): el servicio ya no envía correos ni escribe el historial de forma síncrona;
 * confirma el cambio y publica el evento de dominio (outbox). Estas pruebas lo verifican.
 */
@ExtendWith(MockitoExtension.class)
class RequestServiceImplTest {

    @Mock private RequestRepository repository;
    @Mock private IRequestHistoryService historyService;
    @Mock private IUserService userService;
    @Mock private IRequestEventPublisher eventPublisher;
    @Mock private DocumentRepository documentRepository;
    @InjectMocks private RequestServiceImpl service;

    private Request solicitud(Long id, RequestStatus status) {
        Request r = new Request();
        r.setId(id);
        r.setTitle("Queja por atención");
        r.setEmail("ciudadano@example.com");
        r.setType(RequesType.QUEJA);
        r.setStatus(status);
        r.setMailStatus(false);
        return r;
    }

    private User admin() {
        User u = new User();
        u.setId(1L);
        u.setDocument("1000000001");
        return u;
    }

    @Test
    @DisplayName("save: la solicitud nace en estado CREADO, sin correo enviado, y publica REQUEST_CREATED")
    void saveAsignaEstadoCreadoYPublicaEvento() {
        RequestDTO dto = new RequestDTO("Titulo", "Descripcion", "Nombre Completo", "a@b.com", "12345678",
                "Neiva", "3001234567", RequesType.QUEJA, true, true, LocalDate.now().plusDays(15));
        User actor = admin();
        when(repository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(userService.getAuthenticatedUser()).thenReturn(actor);

        Request result = service.save(dto);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(repository).save(captor.capture());
        assertEquals(RequestStatus.CREADO, captor.getValue().getStatus());
        assertFalse(captor.getValue().getMailStatus());
        assertEquals("Titulo", result.getTitle());
        assertEquals("a@b.com", result.getEmail());

        // El evento se registra DESPUÉS de persistir (ya tiene id) y con el actor autenticado
        InOrder order = inOrder(repository, eventPublisher);
        order.verify(repository).save(any(Request.class));
        order.verify(eventPublisher).publishCreated(result, actor);
    }

    @Test
    @DisplayName("findById: devuelve la solicitud existente")
    void findByIdExistente() {
        Request r = solicitud(1L, RequestStatus.CREADO);
        when(repository.findById(1L)).thenReturn(Optional.of(r));
        assertSame(r, service.findById(1L));
    }

    @Test
    @DisplayName("findById: lanza ResourceNotFoundException si no existe")
    void findByIdInexistente() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L));
    }

    @Test
    @DisplayName("all: delega en el repositorio con la paginación recibida")
    void allDelegaAlRepositorio() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Request> page = new PageImpl<>(List.of(solicitud(1L, RequestStatus.CREADO)));
        when(repository.findAll(ArgumentMatchers.<Specification<Request>>any(), eq(pageable))).thenReturn(page);

        Page<Request> result = service.all("texto", RequestStatus.CREADO, RequesType.QUEJA, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        verify(repository).findAll(ArgumentMatchers.<Specification<Request>>any(), eq(pageable));
    }

    @Test
    @DisplayName("updateStatus a CERRADO: actualiza el estado y publica REQUEST_STATUS_CHANGED (sin I/O síncrono)")
    void updateStatusPublicaEvento() {
        Request r = solicitud(1L, RequestStatus.CREADO);
        User admin = admin();
        when(repository.findById(1L)).thenReturn(Optional.of(r));
        when(userService.getAuthenticatedUser()).thenReturn(admin);
        when(repository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request result = service.updateStatus(1L, RequestStatus.CERRADO, "Resuelto");

        assertEquals(RequestStatus.CERRADO, result.getStatus());
        // mail_status lo marca el consumidor de notificaciones cuando realmente notifica
        assertFalse(result.getMailStatus());
        verify(eventPublisher).publishStatusChanged(r, RequestStatus.CREADO, RequestStatus.CERRADO, admin, "Resuelto");
        verifyNoInteractions(historyService);
    }

    @Test
    @DisplayName("updateStatus a FALTA_DOCUMENTACION: publica el evento con observaciones nulas")
    void updateStatusAFaltaDocumentacion() {
        Request r = solicitud(2L, RequestStatus.CREADO);
        User admin = admin();
        when(repository.findById(2L)).thenReturn(Optional.of(r));
        when(userService.getAuthenticatedUser()).thenReturn(admin);
        when(repository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(2L, RequestStatus.FALTA_DOCUMENTACION, null);

        verify(eventPublisher).publishStatusChanged(r, RequestStatus.CREADO, RequestStatus.FALTA_DOCUMENTACION,
                admin, null);
    }

    @Test
    @DisplayName("updateStatus al mismo estado: 400 de negocio y NO se publica evento")
    void updateStatusMismoEstadoNoPublica() {
        Request r = solicitud(3L, RequestStatus.CERRADO);
        when(repository.findById(3L)).thenReturn(Optional.of(r));

        assertThrows(BusinessRuleException.class, () -> service.updateStatus(3L, RequestStatus.CERRADO, "ya cerrado"));

        verify(repository, never()).save(any(Request.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("updateStatus: lanza ResourceNotFoundException si la solicitud no existe")
    void updateStatusInexistente() {
        when(repository.findById(50L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateStatus(50L, RequestStatus.CERRADO, "x"));
        verifyNoInteractions(eventPublisher, historyService);
    }

    @Test
    @DisplayName("delete: elimina historial, documentos y la solicitud existente")
    void deleteExistente() {
        Request r = solicitud(4L, RequestStatus.CREADO);
        List<Document> docs = List.of(new Document());
        when(repository.findById(4L)).thenReturn(Optional.of(r));
        when(documentRepository.findByRequestId(4L)).thenReturn(docs);

        service.delete(4L);

        verify(historyService).deleteByRequestId(4L);
        verify(documentRepository).deleteAll(docs);
        verify(repository).delete(r);
    }

    @Test
    @DisplayName("delete: lanza ResourceNotFoundException y no borra si no existe")
    void deleteInexistente() {
        when(repository.findById(77L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(77L));
        verify(repository, never()).delete(any(Request.class));
    }
}
