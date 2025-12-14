package com.learning.authservice.signup;

import com.learning.common.dto.TenantType;

/**
 * Base interface for signup requests.
 * Sealed to constrain implementations to Personal and Organization types.
 * 
 * All signup types share these base properties.
 */
public sealed interface SignupRequest permits PersonalSignupData, OrganizationSignupData {

    /**
     * Email address of the user signing up.
     * For organizations, this is the admin email.
     */
    String email();

    /**
     * Password for the new account.
     */
    String password();

    /**
     * Display name of the user.
     * For organizations, this is the admin name.
     */
    String name();

    /**
     * Type of tenant being created (PERSONAL or ORGANIZATION).
     */
    TenantType tenantType();
}
