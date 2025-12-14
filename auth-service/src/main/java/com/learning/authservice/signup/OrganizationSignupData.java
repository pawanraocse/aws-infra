package com.learning.authservice.signup;

import com.learning.common.dto.TenantType;

/**
 * Signup data for organization (B2B) accounts.
 * Includes company details, tier, and subscription limits.
 */
public record OrganizationSignupData(
        String email, // Admin email
        String password,
        String name, // Admin name
        String companyName,
        String tier, // STANDARD, PREMIUM, ENTERPRISE
        Integer maxUsers // Subscription user limit
) implements SignupRequest {

    /**
     * Compact constructor with default values.
     */
    public OrganizationSignupData {
        if (tier == null) {
            tier = "STANDARD";
        }
        if (maxUsers == null) {
            maxUsers = 50;
        }
    }

    @Override
    public TenantType tenantType() {
        return TenantType.ORGANIZATION;
    }
}
