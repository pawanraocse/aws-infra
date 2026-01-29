package com.learning.authservice.signup.actions;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupActionException;
import com.learning.authservice.signup.pipeline.SignupContext;
import com.learning.common.infra.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;

import javax.sql.DataSource;

/**
 * Action to assign roles to user in tenant database.
 * 
 * Order: 50
 * 
 * This is the critical step that creates user_roles in the tenant DB.
 * Sets TenantContext to route to correct database.
 * 
 * For personal tenants (SHARED mode), this action also ensures default roles
 * exist before assignment, as provisioning may be skipped for existing tenants.
 */
@Component
@Order(50)
@Slf4j
public class AssignRolesAction implements SignupAction {

    private final UserRoleService userRoleService;
    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;
    private final JdbcTemplate jdbcTemplate;

    public AssignRolesAction(
            UserRoleService userRoleService,
            CognitoIdentityProviderClient cognitoClient,
            CognitoProperties cognitoProperties,
            @Qualifier("tenantDataSource") DataSource tenantDataSource) {
        this.userRoleService = userRoleService;
        this.cognitoClient = cognitoClient;
        this.cognitoProperties = cognitoProperties;
        this.jdbcTemplate = new JdbcTemplate(tenantDataSource);
    }

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

            String role = ctx.getAssignedRole() != null ? ctx.getAssignedRole()
                    : (ctx.isSsoSignup() ? "viewer" : "admin"); // SSO users get viewer, org creators get admin

            log.info("Assigning role {} to user {} in tenant {}", role, userId, ctx.getTenantId());

            // Set tenant context for tenant DB routing
            TenantContext.setCurrentTenant(ctx.getTenantId());
            try {
                // Ensure roles exist for this tenant (handles existing tenants that missed seeding)
                ensureRolesExist(ctx.getTenantId());
                
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
     * Ensures default roles and permissions exist for the tenant.
     * This is a safety net for tenants that were created before seed-roles was implemented,
     * or when provisioning was skipped because tenant already existed.
     */
    private void ensureRolesExist(String tenantId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM roles WHERE tenant_id = ?", Integer.class, tenantId);
            
            if (count != null && count > 0) {
                log.debug("Roles already exist for tenant {}", tenantId);
                return;
            }
            
            log.info("Seeding default roles for tenant {} (first-time setup)", tenantId);
            
            // Insert default roles
            jdbcTemplate.update("""
                    INSERT INTO roles (id, tenant_id, name, description, scope, access_level, created_at, updated_at)
                    VALUES 
                    ('admin', ?, 'admin', 'Full administrative access', 'TENANT', 'ADMIN', NOW(), NOW()),
                    ('editor', ?, 'editor', 'Can edit content', 'TENANT', 'WRITE', NOW(), NOW()),
                    ('viewer', ?, 'viewer', 'Read-only access', 'TENANT', 'READ', NOW(), NOW())
                    ON CONFLICT (tenant_id, name) DO NOTHING
                    """, tenantId, tenantId, tenantId);
            
            // Insert default permissions
            jdbcTemplate.update("""
                    INSERT INTO permissions (id, tenant_id, resource, action, description, created_at)
                    VALUES 
                    ('entries:read', ?, 'entries', 'read', 'Read entries', NOW()),
                    ('entries:write', ?, 'entries', 'write', 'Write entries', NOW()),
                    ('entries:delete', ?, 'entries', 'delete', 'Delete entries', NOW()),
                    ('users:read', ?, 'users', 'read', 'View users', NOW()),
                    ('users:manage', ?, 'users', 'manage', 'Manage users', NOW()),
                    ('roles:manage', ?, 'roles', 'manage', 'Manage roles', NOW())
                    ON CONFLICT (tenant_id, resource, action) DO NOTHING
                    """, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId);
            
            // Insert role-permission mappings
            jdbcTemplate.update("""
                    INSERT INTO role_permissions (tenant_id, role_id, permission_id, created_at)
                    VALUES 
                    (?, 'admin', 'entries:read', NOW()),
                    (?, 'admin', 'entries:write', NOW()),
                    (?, 'admin', 'entries:delete', NOW()),
                    (?, 'admin', 'users:read', NOW()),
                    (?, 'admin', 'users:manage', NOW()),
                    (?, 'admin', 'roles:manage', NOW()),
                    (?, 'editor', 'entries:read', NOW()),
                    (?, 'editor', 'entries:write', NOW()),
                    (?, 'viewer', 'entries:read', NOW())
                    ON CONFLICT (tenant_id, role_id, permission_id) DO NOTHING
                    """, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId);
            
            log.info("Default roles seeded for tenant {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to seed roles for tenant {}: {}", tenantId, e.getMessage());
            throw e;
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
