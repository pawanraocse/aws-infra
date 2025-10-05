package com.learning.awsinfra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Response DTO for Entry.")
public record EntryResponseDto(
    @Schema(description = "Entry UUID", example = "b3b7c2e2-8c2a-4e2a-9c2a-8c2a4e2a9c2a")
    String id,
    @Schema(description = "Entry metadata as key-value pairs", example = "{\"type\":\"invoice\",\"amount\":100}")
    Map<String, Object> metadata,
    @Schema(description = "Creation timestamp", example = "2025-10-05T12:34:56Z")
    Instant createdAt,
    @Schema(description = "Last update timestamp", example = "2025-10-05T12:35:56Z")
    Instant updatedAt
) {}

