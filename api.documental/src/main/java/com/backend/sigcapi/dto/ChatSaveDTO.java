package com.backend.sigcapi.dto;

import com.backend.sigcapi.enums.ChatType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatSaveDTO(
    @NotNull Long sesionId,
    @NotBlank String mensaje,
    @NotNull ChatType type
) {
}
