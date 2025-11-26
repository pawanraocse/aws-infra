package com.learning.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for tenant provisioning.
 * Shared across all services that need to create tenants.
 */
public record ProvisionTenantRequest(
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}$", message = "Tenant id must be 3-64 chars alphanum, underscore or hyphen") String id,

        @NotBlank(message = "Name is required") String name,

        @Pattern(regexp = "^(SCHEMA|DATABASE)$", message = "storageMode must be SCHEMA or DATABASE") String storageMode,

        @Pattern(regexp = "^(STANDARD|PREMIUM|ENTERPRISE)$", message = "slaTier must be STANDARD, PREMIUM or ENTERPRISE") String slaTier,

        @NotNull(message = "Tenant type is required") TenantType tenantType,

        @Email(message = "Valid owner email is required") String ownerEmail,

        @Min(value = 1, message = "Max users must be at least 1") @Max(value = 10000, message = "Max users cannot exceed 10000") Integer maxUsers) {
    /**
     * Factory method for B2C personal tenant creation.
     * Creates a single-user workspace with STANDARD tier.
     */
    public static ProvisionTenantRequest forPersonal(String id, String email) {
        return new ProvisionTenantRequest(
                id,
                email + "'s Workspace",
                "DATABASE",
                "STANDARD",
                TenantType.PERSONAL,
                email,
                1);
    }

    /**
     * Factory method for B2B organization tenant creation.
     * User limit based on tier: STANDARD=50, PREMIUM=200, ENTERPRISE=10000
     */
    public static ProvisionTenantRequest forOrganization(
            String id,
            String name,
            String adminEmail,
            String tier) {
        int maxUsers = switch (tier) {
            case "STANDARD" -> 50;
            case "PREMIUM" -> 200;
            case "ENTERPRISE" -> 10000;
            default -> 50;
        };

        return new ProvisionTenantRequest(
                id,
                name,
                "DATABASE",
                tier,
                TenantType.ORGANIZATION,
                adminEmail,
                maxUsers);
    }
}
