package com.backend.sigcapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UsageDTO(
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens
) {}
