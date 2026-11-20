package com.backend.sigcapi.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.entities.Standard;
import com.backend.sigcapi.services.IStandardService;
import com.backend.sigcapi.util.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/standards")
public class StandardController {

    private final IStandardService service;

	public StandardController(IStandardService service) {
		this.service = service;
	}

    @GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<List<Standard>>> all() {
		List<Standard> standards = service.all();

		if (standards.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay estandares registrados actualmente.", standards));
		}

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de estandares obtenida con éxito.", standards));
	} 

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ApiResponse<Standard>> save(@Valid @RequestBody Standard standard) {
		Standard standardSave = service.save(standard);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Estandar registrado.", standardSave));
	}
    
}
