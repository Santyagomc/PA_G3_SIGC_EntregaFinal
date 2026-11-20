package com.backend.sigcapi.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record SesionDTO (
    Long id,
    @NotBlank String fullName,
    LocalDateTime createdAt,
    List<ChatDTO> chats) {
}
