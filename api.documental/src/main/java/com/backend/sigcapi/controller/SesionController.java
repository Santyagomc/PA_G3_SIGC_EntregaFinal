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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.dto.PageResponseDTO;
import com.backend.sigcapi.dto.SesionDTO;
import com.backend.sigcapi.dto.SesionListDTO;
import com.backend.sigcapi.services.ISesionService;
import com.backend.sigcapi.util.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/sessions")
public class SesionController {

    private final ISesionService service;

    public SesionController(ISesionService service) {
        this.service = service;
    }

    @GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<PageResponseDTO<SesionListDTO>>> all(
		@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "10") int size) {

		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());

		Page<SesionListDTO> sessionsPage = service.all(from, to, pageable);

		if (sessionsPage.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay sesiones registradas actualmente."));
		}

		PageResponseDTO<SesionListDTO> data = new PageResponseDTO<>(
				sessionsPage.getContent(),
				sessionsPage.getTotalElements(),
				sessionsPage.getTotalPages(),
				sessionsPage.getNumber());

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de sesiones obtenida con éxito.", data));
	}
    
    @GetMapping("/findById/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<SesionDTO>> getById(@PathVariable Long id) {
		SesionDTO session = service.findById(id);
		return ResponseEntity.ok(new ApiResponse<>(true, "Sesión encontrada.", session));
	}
    
    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ApiResponse<SesionDTO>> save(@Valid @RequestParam String fullName) {
		SesionDTO session = service.save(fullName);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Sesión registrada.", session));
	}

    @DeleteMapping("/logout/{id}")
	@ResponseStatus(code = HttpStatus.NO_CONTENT)
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		service.logOut(id);
		return ResponseEntity.noContent().build();
	}
    
}
