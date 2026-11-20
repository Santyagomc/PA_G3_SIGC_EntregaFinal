package com.backend.sigcapi.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import com.backend.sigcapi.entities.Document;

public interface IDocumentService {
    Page<Document> all(Long idRequest, LocalDate from, LocalDate to, Pageable pageable);
    Document findById(Long id);
    List<Document> saveAll(List<MultipartFile> files, Long idRequest);
    void delete(Long id);
}