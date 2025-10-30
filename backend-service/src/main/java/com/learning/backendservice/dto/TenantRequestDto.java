package com.learning.backendservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new tenant")
public class TenantRequestDto {

    @NotBlank(message = "Tenant ID is required")
    @Size(min = 3, max = 50, message = "Tenant ID must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Tenant ID must contain only lowercase letters, numbers, and hyphens")
    @Schema(description = "Unique tenant identifier", example = "acme-corp")
    private String tenantId;

    @NotBlank(message = "Tenant name is required")
    @Size(max = 255, message = "Tenant name must not exceed 255 characters")
    @Schema(description = "Tenant display name", example = "Acme Corporation")
    private String tenantName;
}
