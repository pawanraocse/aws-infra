package com.learning.platformservice.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProvisionTenantRequest(
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}$") String id,
        @NotBlank String name,
        @Pattern(regexp = "^(SCHEMA|DATABASE)$") String storageMode,
        @Pattern(regexp = "^(STANDARD|PREMIUM)$") String slaTier
) {
}

