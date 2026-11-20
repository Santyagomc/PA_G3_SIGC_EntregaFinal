package com.backend.sigcapi.dto;

import com.backend.sigcapi.enums.Operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RuleDTO(
    @NotBlank
    @Size(max = 100)
    @NotNull(message = "El campo fullName es obligatorio")
    String fullName,
    @NotNull Long standardId,
    @NotNull(message = "El campo operator es obligatorio")
	Operator operator,
    @NotBlank
    @Size(max = 100)
    @NotNull(message = "El campo value es obligatorio")
    String value,
    @NotBlank
    @NotNull(message = "El campo suggestion es obligatorio")
    String suggestion,
    @NotNull(message = "El campo isBlocking es obligatorio")
    Boolean isBlocking
) {}
