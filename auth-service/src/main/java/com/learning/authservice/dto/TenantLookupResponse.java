package com.learning.authservice.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Response DTO for tenant lookup in the login flow.
 * Contains all information needed to display a tenant in the selector.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantLookupResponse {

    /**
     * Unique tenant identifier.
     */
    private String tenantId;

    /**
     * Display name of the tenant.
     */
    private String tenantName;

    /**
     * Type of tenant: PERSONAL or ORGANIZATION.
     */
    private String tenantType;

    /**
     * Company name (for organizations).
     */
    private String companyName;

    /**
     * URL to tenant's logo image.
     */
    private String logoUrl;

    /**
     * Whether SSO is enabled for this tenant.
     */
    private boolean ssoEnabled;

    /**
     * SSO provider type (OKTA, AZURE_AD, etc.) if SSO is enabled.
     */
    private String ssoProvider;

    /**
     * User's role hint in this tenant (owner, admin, member, guest).
     */
    private String roleHint;

    /**
     * Whether the user owns this tenant.
     */
    private boolean isOwner;

    /**
     * Whether this is the user's default workspace.
     */
    private boolean isDefault;

    /**
     * When the user last accessed this tenant.
     */
    private OffsetDateTime lastAccessedAt;
}
