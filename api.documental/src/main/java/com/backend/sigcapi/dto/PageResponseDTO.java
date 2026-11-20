package com.backend.sigcapi.dto;

import java.util.List;

public record PageResponseDTO<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int currentPage
) {}
