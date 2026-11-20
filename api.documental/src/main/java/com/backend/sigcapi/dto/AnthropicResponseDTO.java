package com.backend.sigcapi.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnthropicResponseDTO(
    String id,
    String type,
    String role,
    String model,
    List<AnthropicContentDTO> content,
    @JsonProperty("stop_reason") String stopReason,
    @JsonProperty("stop_sequence") String stopSequence,
    UsageDTO usage
) {}
