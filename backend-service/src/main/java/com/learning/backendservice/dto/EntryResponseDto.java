package com.learning.backendservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entry response")
public class EntryResponseDto {

    @Schema(description = "Entry ID", example = "1")
    private Long id;

    @Schema(description = "Unique key", example = "api_key")
    private String key;

    @Schema(description = "Value", example = "sk-123456789")
    private String value;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Created by user ID")
    private String createdBy;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Last updated by user ID")
    private String updatedBy;
}
