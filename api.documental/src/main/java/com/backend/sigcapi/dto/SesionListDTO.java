package com.backend.sigcapi.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SesionListDTO(
    @NotNull Long id,
    @NotBlank String fullName,
    @NotNull LocalDateTime createdAt,
    LocalDateTime closureAt
) {}
