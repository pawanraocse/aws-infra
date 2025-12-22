package com.learning.authservice.signup;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.common.dto.SignupResponse;
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;

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
    private final UserRoleService userRoleService;
    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    @Override
    public SignupResponse signup(SignupRequest request) {
        log.info("Processing {} signup for: {}", request.tenantType(), request.email());

        try {
            // 1. Generate tenant ID (polymorphic - different strategy per type)
            String tenantId = tenantIdGenerator.generate(request);
            log.debug("Generated tenant ID: {}", tenantId);

            // 2. Provision tenant (polymorphic - org includes tier/maxUsers)
            // This also creates the user_tenant_membership in platform DB
            tenantProvisioner.provision(request, tenantId);

            // 3. Register user in Cognito (or skip if exists)
            // Enables multi-account per email: existing users can create additional orgs
            String role = determineRole(request);
            CognitoUserRegistrar.RegistrationResult result = cognitoUserRegistrar.registerIfNotExists(
                    request.email(),
                    request.password(),
                    request.name(),
                    tenantId,
                    role);

            log.info("Signup completed: email={} tenantId={} result={}",
                    request.email(), tenantId, result);

            // 4. For existing users, assign role immediately (no email verification step)
            if (result == CognitoUserRegistrar.RegistrationResult.ALREADY_EXISTS) {
                assignRoleForExistingUser(request.email(), tenantId, role);
            }

            // Message varies based on whether user was created or already existed
            String message = switch (result) {
                case CREATED -> "Signup complete. Please verify your email.";
                case ALREADY_EXISTS -> "New workspace created! Please login to access it.";
            };

            // Only need email verification for newly created users
            boolean confirmed = (result == CognitoUserRegistrar.RegistrationResult.ALREADY_EXISTS);

            return SignupResponse.success(message, tenantId, confirmed);

        } catch (IllegalArgumentException e) {
            log.warn("Signup validation failed: {}", e.getMessage());
            return SignupResponse.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Signup failed: email={} error={}", request.email(), e.getMessage(), e);
            return SignupResponse.failure(e.getMessage());
        }
    }

    /**
     * Assign admin role to existing user in the new tenant database.
     * This is needed because existing users skip email verification,
     * so the role assignment in verify() is never called.
     */
    private void assignRoleForExistingUser(String email, String tenantId, String role) {
        log.info("Assigning role for existing user: email={} tenantId={} role={}", email, tenantId, role);

        try {
            // Get user's sub from Cognito
            AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(email)
                    .build();

            AdminGetUserResponse userResponse = cognitoClient.adminGetUser(getUserRequest);
            String userId = userResponse.userAttributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Failed to get user sub from Cognito"));

            // Set tenant context and assign role
            TenantContext.setCurrentTenant(tenantId);
            try {
                userRoleService.assignRole(userId, role, "system");
                log.info("✅ Admin role assigned for existing user: userId={} tenantId={}", userId, tenantId);
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Failed to assign role for existing user: email={} tenantId={} error={}",
                    email, tenantId, e.getMessage(), e);
            // Don't fail the signup - user can be added manually later
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
