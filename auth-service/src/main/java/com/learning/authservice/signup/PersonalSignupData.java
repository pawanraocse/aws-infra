package com.learning.authservice.signup;

import com.learning.common.dto.TenantType;

/**
 * Signup data for personal (B2C) accounts.
 * Minimal fields - just user info, no organization details.
 */
public record PersonalSignupData(
        String email,
        String password,
        String name) implements SignupRequest {

    @Override
    public TenantType tenantType() {
        return TenantType.PERSONAL;
    }
}
