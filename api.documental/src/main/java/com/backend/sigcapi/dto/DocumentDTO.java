package com.backend.sigcapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentDTO(
    @NotNull(message = "El campo idRequest es obligatorio") Long idRequest,
    @NotNull(message = "El campo fileName es obligatorio") @NotBlank @Size(max = 255) String fileName) {
}
