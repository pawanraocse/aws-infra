package com.learning.awsinfra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Request DTO for creating/updating an Entry.")
public record EntryRequestDto(
    @Schema(description = "Entry metadata as key-value pairs", example = "{\"type\":\"invoice\",\"amount\":100}")
    @NotNull @Size(min = 1)
    Map<String, Object> metadata
) {}

