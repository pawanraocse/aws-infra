package com.learning.authservice.signup;

import com.learning.common.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of SignupService.
 * Orchestrates the signup flow by delegating to specialized components.
 * 
 * Single Responsibility: Coordinates the signup steps.
 * Open/Closed: New signup types can be added by implementing SignupRequest.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignupServiceImpl implements SignupService {

    private final TenantIdGenerator tenantIdGenerator;
    private final TenantProvisioner tenantProvisioner;
    private final CognitoUserRegistrar cognitoUserRegistrar;

    @Override
    public SignupResponse signup(SignupRequest request) {
        log.info("Processing {} signup for: {}", request.tenantType(), request.email());

        try {
            // 1. Generate tenant ID (polymorphic - different strategy per type)
            String tenantId = tenantIdGenerator.generate(request);
            log.debug("Generated tenant ID: {}", tenantId);

            // 2. Provision tenant (polymorphic - org includes tier/maxUsers)
            tenantProvisioner.provision(request, tenantId);

            // 3. Register user in Cognito (ALWAYS via signUp for email verification)
            // NOTE: tenantType is NOT stored in Cognito - frontend looks it up from
            // platform DB
            String role = determineRole(request);
            boolean confirmed = cognitoUserRegistrar.register(
                    request.email(),
                    request.password(),
                    request.name(),
                    tenantId,
                    role);

            log.info("Signup completed: email={} tenantId={} confirmed={}",
                    request.email(), tenantId, confirmed);

            return SignupResponse.success(
                    confirmed ? "Signup complete. Please login."
                            : "Signup complete. Please verify your email.",
                    tenantId,
                    confirmed);

        } catch (IllegalArgumentException e) {
            log.warn("Signup validation failed: {}", e.getMessage());
            return SignupResponse.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Signup failed: email={} error={}", request.email(), e.getMessage(), e);
            return SignupResponse.failure(e.getMessage());
        }
    }

    /**
     * Determine the initial role for the user.
     * Both personal and organization admins get admin role.
     */
    private String determineRole(SignupRequest request) {
        // Both types get admin role initially
        return "admin";
    }
}
