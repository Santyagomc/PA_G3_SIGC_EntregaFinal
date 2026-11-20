package com.backend.sigcapi.dto;

import java.time.LocalDateTime;

import com.backend.sigcapi.enums.ChatType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatDTO(
    @NotBlank String mensaje,
    @NotNull ChatType type,
    LocalDateTime createdAt) {
}
