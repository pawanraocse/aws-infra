package com.learning.awsinfra.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response DTO for key-value Entry.")
public record EntryResponseDto(
        @Schema(description = "Entry ID", example = "1")
        Long id,
        @Schema(description = "Entry key", example = "type")
        String key,
        @Schema(description = "Entry value", example = "invoice")
        String value
)  { }
