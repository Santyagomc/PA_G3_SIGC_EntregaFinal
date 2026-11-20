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

import com.backend.sigcapi.dto.RuleDTO;
import com.backend.sigcapi.entities.Rule;
import com.backend.sigcapi.services.IRuleService;
import com.backend.sigcapi.util.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/rules")
public class RuleController {

    private final IRuleService service;

	public RuleController(IRuleService service) {
		this.service = service;
	}

	@GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<List<Rule>>> all() {
		List<Rule> rules = service.all();

		if (rules.isEmpty()) {
			return ResponseEntity.ok(new ApiResponse<>(true, "No hay reglas registradas actualmente.", rules));
		}

		return ResponseEntity.ok(new ApiResponse<>(true, "Lista de reglas obtenida con éxito.", rules));
	}   

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ApiResponse<Rule>> save(@Valid @RequestBody RuleDTO rule) {
		Rule ruleSave = service.save(rule);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Regla registrada.", ruleSave));
	}
    
}
