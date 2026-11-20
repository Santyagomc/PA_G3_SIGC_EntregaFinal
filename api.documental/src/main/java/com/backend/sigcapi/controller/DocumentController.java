package com.backend.sigcapi.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.dto.PageResponseDTO;
import com.backend.sigcapi.entities.Document;
import com.backend.sigcapi.services.IDocumentService;
import com.backend.sigcapi.util.ApiResponse;

@RestController
@RequestMapping("/documents")
public class DocumentController {

	private final IDocumentService service;

	public DocumentController(IDocumentService service) {
		this.service = service;
	}

	@GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<PageResponseDTO<Document>>> all(
			@RequestParam(name = "request", required = false) Long idRequest, 
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "10") int size) {

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());

		Page<Document> documentsPage = service.all(idRequest, from, to, pageable);

		if (documentsPage.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay documentos registrados actualmente."));
		}

		PageResponseDTO<Document> data = new PageResponseDTO<>(
				documentsPage.getContent(),
				documentsPage.getTotalElements(),
				documentsPage.getTotalPages(),
				documentsPage.getNumber());

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de documentos obtenida con éxito.", data));
	}

	@GetMapping("/findById/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<Document>> getById(@PathVariable Long id) {
		Document document = service.findById(id);
		return ResponseEntity.ok(new ApiResponse<>(true, "Documento encontrado.", document));
	}

	@DeleteMapping("/delete/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(code = HttpStatus.NO_CONTENT)
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
