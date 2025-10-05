package com.learning.awsinfra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request DTO for creating/updating a key-value Entry.")
public record EntryRequestDto(
    @Schema(description = "Entry key", example = "type")
    @NotBlank
    String key,
    @Schema(description = "Entry value", example = "invoice")
    @NotBlank
    String value
) {}
