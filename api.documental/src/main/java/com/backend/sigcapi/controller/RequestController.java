package com.backend.sigcapi.controller;

import java.time.LocalDate;
import java.util.List;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.backend.sigcapi.dto.PageResponseDTO;
import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.entities.RequestHistory;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;
import com.backend.sigcapi.services.IDocumentService;
import com.backend.sigcapi.services.IRequestHistoryService;
import com.backend.sigcapi.services.IRequestService;
import com.backend.sigcapi.services.IRuleService;
import com.backend.sigcapi.services.IUserService;
import com.backend.sigcapi.util.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/requests")
public class RequestController {

	private final IRequestService service;
	private final IRuleService ruleService;
	private final IUserService userService;
	private final IDocumentService documentService;
	private final IRequestHistoryService historyService;

	public RequestController(IRequestService service,
			IRuleService ruleService,
			IUserService userService,
			IDocumentService documentService,
			IRequestHistoryService historyService) {
		this.service = service;
		this.ruleService = ruleService;
		this.userService = userService;
		this.documentService = documentService;
		this.historyService = historyService;
	}

	@GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<PageResponseDTO<Request>>> getAll(
			@RequestParam(name = "search", required = false) String search,
			@RequestParam(name = "status", required = false) RequestStatus status,
			@RequestParam(name = "type", required = false) RequesType type,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "10") int size) {

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());

		Page<Request> requestsPage = service.all(search, status, type, from, to, pageable);

		if (requestsPage.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay solicitudes para mostrar."));
		}

		PageResponseDTO<Request> data = new PageResponseDTO<>(
				requestsPage.getContent(),
				requestsPage.getTotalElements(),
				requestsPage.getTotalPages(),
				requestsPage.getNumber());

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de solicitudes obtenida con éxito.", data));
	}

	@GetMapping("/findById/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<Request>> getById(@PathVariable Long id) {
		Request request = service.findById(id);
		return ResponseEntity.ok(new ApiResponse<>(true, "Solicitud encontrada.", request));
	}

	@GetMapping("/my")
	public ResponseEntity<ApiResponse<PageResponseDTO<Request>>> getMyRequests(
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "10") int size) {

		User user = userService.getAuthenticatedUser();

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());

		Page<Request> requestsPage = service.all(user.getDocument(), null, null, from, to, pageable);

		if (requestsPage.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay solicitudes para mostrar."));
		}

		PageResponseDTO<Request> data = new PageResponseDTO<>(
				requestsPage.getContent(),
				requestsPage.getTotalElements(),
				requestsPage.getTotalPages(),
				requestsPage.getNumber());

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de mis solicitudes obtenida con éxito.", data));
	}

	@PostMapping("/save")
	@ResponseStatus(HttpStatus.CREATED)
	@org.springframework.transaction.annotation.Transactional
	public ResponseEntity<ApiResponse<Request>> save(@Valid @RequestPart("request") RequestDTO request,
			@RequestPart("files") List<MultipartFile> files) {

		ruleService.consultLegalDiagnosis(request, files.size());

		Request requestSave = service.save(request);

		documentService.saveAll(files, requestSave.getId());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ApiResponse<>(true,
						"Radicación exitosa. El acuse al ciudadano y la auditoría se procesan de forma asíncrona (Kafka).",
						requestSave));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<ApiResponse<Request>> updateStatus(@PathVariable Long id,
			@RequestParam("status") RequestStatus newStatus,
			@RequestParam(name = "observations", required = false) String observations) {

		Request requestUpdated = service.updateStatus(id, newStatus, observations);

		return ResponseEntity
				.status(HttpStatus.OK)
				.body(new ApiResponse<>(true,
						"Estado actualizado. Evento REQUEST_STATUS_CHANGED registrado: la auditoría y la notificación se procesan de forma asíncrona.",
						requestUpdated));
	}

	/**
	 * Historial de auditoría de una PQR. Es el resultado verificable del consumidor Kafka
	 * AuditEventConsumer (grupo sigc-audit): cada fila corresponde a un evento procesado.
	 */
	@GetMapping("/{id}/history")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<PageResponseDTO<RequestHistory>>> getHistory(@PathVariable Long id,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {

		service.findById(id); // 404 si la PQR no existe

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
				Sort.by("changedAt").ascending());
		Page<RequestHistory> history = historyService.getHistoryByRequestId(id, from, to, pageable);

		PageResponseDTO<RequestHistory> data = new PageResponseDTO<>(
				history.getContent(),
				history.getTotalElements(),
				history.getTotalPages(),
				history.getNumber());

		return ResponseEntity.ok(new ApiResponse<>(true, "Historial de auditoría de la solicitud " + id + ".", data));
	}

	@DeleteMapping("/delete/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(code = HttpStatus.NO_CONTENT)
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
