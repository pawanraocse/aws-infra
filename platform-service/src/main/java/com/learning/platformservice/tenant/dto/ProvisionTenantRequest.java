package com.learning.platformservice.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProvisionTenantRequest(
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}$", message = "Tenant id must be 3-64 chars alphanum, underscore or hyphen") String id,
        @NotBlank(message = "Name is required") String name,
        @Pattern(regexp = "^(SCHEMA|DATABASE)$", message = "storageMode must be SCHEMA or DATABASE") String storageMode,
        @Pattern(regexp = "^(STANDARD|PREMIUM|ENTERPRISE)$", message = "slaTier must be STANDARD, PREMIUM or ENTERPRISE") String slaTier
) {
}
