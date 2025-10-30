package com.learning.backendservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create or update an entry")
public class EntryRequestDto {

    @NotBlank(message = "Key is required")
    @Size(max = 255, message = "Key must not exceed 255 characters")
    @Schema(description = "Unique key within tenant", example = "api_key")
    private String key;

    @NotBlank(message = "Value is required")
    @Schema(description = "Value for the key", example = "sk-123456789")
    private String value;
}
