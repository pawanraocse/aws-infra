package com.learning.authservice.signup.actions;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupActionException;
import com.learning.authservice.signup.pipeline.SignupContext;
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;

/**
 * Action to assign roles to user in tenant database.
 * 
 * Order: 50
 * 
 * This is the critical step that creates user_roles in the tenant DB.
 * Sets TenantContext to route to correct database.
 */
@Component
@Order(50)
@Slf4j
@RequiredArgsConstructor
public class AssignRolesAction implements SignupAction {

    private final UserRoleService userRoleService;
    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    @Override
    public String getName() {
        return "AssignRoles";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public boolean supports(SignupContext ctx) {
        // All signup types need role assignment
        return true;
    }

    @Override
    public boolean isAlreadyDone(SignupContext ctx) {
        // Check if user already has roles in tenant DB
        // If tenant doesn't exist yet, this will return false (not done)
        try {
            String userId = getUserId(ctx);
            if (userId == null) {
                return false;
            }

            // Only check if tenant exists
            if (ctx.getTenantId() == null) {
                return false;
            }

            TenantContext.setCurrentTenant(ctx.getTenantId());
            try {
                // Check if user already has roles
                var roles = userRoleService.getUserRoles(userId);
                if (roles != null && !roles.isEmpty()) {
                    log.debug("User {} already has roles in tenant {}", userId, ctx.getTenantId());
                    return true;
                }
                return false;
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            // Tenant DB might not exist yet - that's fine, return false
            log.debug("Cannot check roles (tenant may not exist yet): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void execute(SignupContext ctx) throws SignupActionException {
        try {
            String userId = getUserId(ctx);
            if (userId == null) {
                throw new SignupActionException(getName(), "Cannot get user ID for role assignment");
            }

            String role = ctx.getAssignedRole() != null ? ctx.getAssignedRole() : "admin";

            log.info("Assigning role {} to user {} in tenant {}", role, userId, ctx.getTenantId());

            // Set tenant context for tenant DB routing
            TenantContext.setCurrentTenant(ctx.getTenantId());
            try {
                userRoleService.assignRole(userId, role, "system");
                log.info("Role assigned successfully");
            } finally {
                TenantContext.clear();
            }

        } catch (SignupActionException e) {
            throw e;
        } catch (Exception e) {
            throw new SignupActionException(getName(),
                    "Failed to assign roles: " + e.getMessage(), e);
        }
    }

    /**
     * Get user ID (Cognito sub) from context or Cognito.
     */
    private String getUserId(SignupContext ctx) {
        // First check if cognitoUserId is already set (SSO case)
        if (ctx.getCognitoUserId() != null) {
            return ctx.getCognitoUserId();
        }

        // Look up from Cognito
        try {
            var response = cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(ctx.getEmail())
                    .build());

            String sub = response.userAttributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse(null);

            // Cache in context
            if (sub != null) {
                ctx.setCognitoUserId(sub);
            }

            return sub;
        } catch (Exception e) {
            log.error("Failed to get user ID from Cognito: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void rollback(SignupContext ctx) {
        // Role deletion is complex - log for manual cleanup
        log.warn("Rollback requested for roles of user {} in tenant {}: manual cleanup may be needed",
                ctx.getCognitoUserId(), ctx.getTenantId());
    }
}
