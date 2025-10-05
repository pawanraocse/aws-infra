package com.learning.awsinfra.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard error response model.")
public record ErrorResponse(
    @Schema(description = "Error code", example = "ENTRY_NOT_FOUND")
    String code,
    @Schema(description = "Error message", example = "Entry not found")
    String message,
    @Schema(description = "Additional error details", example = "{}")
    Object details
) {}
