package com.learning.authservice.signup;

import com.learning.common.dto.SignupResponse;

/**
 * Service interface for handling user signup.
 * Follows Interface Segregation Principle - single method for signup.
 */
public interface SignupService {

    /**
     * Process a signup request (personal or organization).
     * 
     * @param request the signup data (polymorphic - PersonalSignupData or
     *                OrganizationSignupData)
     * @return SignupResponse with success status and tenant ID
     */
    SignupResponse signup(SignupRequest request);
}
