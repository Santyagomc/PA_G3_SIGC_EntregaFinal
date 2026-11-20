package com.backend.sigcapi.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.backend.sigcapi.entities.Document;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.repositories.DocumentRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.backend.sigcapi.services.implement.DocumentServiceImpl;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock private DocumentRepository repository;
    @Mock private RequestRepository requestRepository;
    private DocumentServiceImpl service;

    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(repository, requestRepository);
        ReflectionTestUtils.setField(service, "storagePath", tmp.toString());
    }

    @Test
    @DisplayName("saveAll: escribe el archivo en disco con nombre único, conserva la extensión y lo asocia a la solicitud")
    void saveAllGuardaArchivos() {
        Request req = new Request();
        req.setId(1L);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("files", "informe.pdf", "application/pdf", "contenido".getBytes());

        List<Document> result = service.saveAll(List.of(file), 1L);

        assertEquals(1, result.size());
        Document d = result.get(0);
        assertEquals("informe.pdf", d.getOriginalName());
        assertTrue(d.getFileName().endsWith(".pdf"));
        assertNotEquals("informe.pdf", d.getFileName());
        assertSame(req, d.getRequest());
        assertTrue(Files.exists(Path.of(d.getFilePath())));
    }

    @Test
    @DisplayName("saveAll: con dos archivos guarda dos documentos distintos")
    void saveAllVariosArchivos() {
        Request req = new Request();
        req.setId(1L);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile a = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MockMultipartFile b = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());

        List<Document> result = service.saveAll(List.of(a, b), 1L);

        assertEquals(2, result.size());
        assertNotEquals(result.get(0).getFileName(), result.get(1).getFileName());
        verify(repository, times(2)).save(any(Document.class));
    }

    @Test
    @DisplayName("saveAll: lanza ResourceNotFoundException si la solicitud no existe y no guarda nada")
    void saveAllSolicitudInexistente() {
        when(requestRepository.findById(9L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());

        assertThrows(ResourceNotFoundException.class, () -> service.saveAll(List.of(file), 9L));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("findById: lanza ResourceNotFoundException si no existe")
    void findByIdInexistente() {
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(5L));
    }

    @Test
    @DisplayName("delete: elimina el documento existente")
    void deleteExistente() {
        Document d = new Document();
        when(repository.findById(2L)).thenReturn(Optional.of(d));
        service.delete(2L);
        verify(repository).delete(d);
    }

    @Test
    @DisplayName("delete: lanza ResourceNotFoundException si no existe")
    void deleteInexistente() {
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(2L));
    }
}
