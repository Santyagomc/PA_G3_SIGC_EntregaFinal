package com.backend.sigcapi.services.implement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.backend.sigcapi.entities.Document;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.repositories.DocumentRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.backend.sigcapi.repositories.specs.DocumentSpecifications;
import com.backend.sigcapi.services.IDocumentService;

@Service
public class DocumentServiceImpl implements IDocumentService {

    private final DocumentRepository repository;
    private final RequestRepository repositoryRequest;

    @Value("${storage.documents.path}")
    private String storagePath;

    public DocumentServiceImpl(DocumentRepository repository, RequestRepository repositoryRequest) {
        this.repository = repository;
        this.repositoryRequest = repositoryRequest;
    }

    @Override
    public Page<Document> all(Long idRequest, LocalDate from, LocalDate to, Pageable pageable) {

        Specification<Document> spec = Specification
                .where(DocumentSpecifications.createdBetween(from, to))
                .and(DocumentSpecifications.hasRequest(idRequest));

        return repository.findAll(spec, pageable);
    }

    @Override
    public Document findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el documento con ID: " + id));
    }

    public List<Document> saveAll(List<MultipartFile> files, Long idRequest) {
        Request request = repositoryRequest.findById(idRequest)
                .orElseThrow(() -> new ResourceNotFoundException("La solicitud no existe."));

        return files.stream().map(file -> {
            try {

                String originalFileName = file.getOriginalFilename();
                String extension = "";
                if (originalFileName != null && originalFileName.contains(".")) {
                    extension = originalFileName.substring(originalFileName.lastIndexOf("."));
                }

                String uniqueFileName = UUID.randomUUID().toString() + extension;

                Path path = Paths.get(storagePath).toAbsolutePath().normalize().resolve(uniqueFileName);

                // Crear directorios y guardar archivo físico
                Files.createDirectories(path.getParent());
                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

                Document document = new Document();
                document.setFileName(uniqueFileName); 
                document.setFilePath(path.toString());
                document.setRequest(request);
                document.setOriginalName(originalFileName);

                return repository.save(document);
            } catch (IOException e) {
                throw new RuntimeException("Error al guardar el archivo físico: " + file.getOriginalFilename(), e);
            }
        }).toList();
    }

    @Override
    public void delete(Long id) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El documento no existe"));
        repository.delete(document);
    }
}