package com.learning.platformservice.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating initial organization settings during signup.
 * Used by auth-service's CreateOrgSettingsAction during organization signup
 * flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationSettingsRequest {

    /**
     * Tenant ID (already created by ProvisionTenantAction).
     */
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    /**
     * Company name for the organization.
     */
    @NotBlank(message = "Company name is required")
    private String companyName;

    /**
     * Subscription tier (e.g., STANDARD, PREMIUM, ENTERPRISE).
     */
    private String tier;
}
