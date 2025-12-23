package com.learning.common.infra.security;

import com.learning.common.infra.cache.CacheNames;
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Set;

/**
 * Permission evaluator that checks role-based access.
 * 
 * <p>
 * Role lookup is done via RoleLookupService which queries the database,
 * replacing the previous X-Role header approach for better security.
 * </p>
 * 
 * <p>
 * With the simplified permission model:
 * </p>
 * <ul>
 * <li>Admin/Super-admin roles get full access (bypass)</li>
 * <li>Editor role can read/write resources</li>
 * <li>Viewer role can only read</li>
 * <li>Guest role has minimal access</li>
 * </ul>
 * 
 * <p>
 * Falls back to remote auth-service call for complex permission checks.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class RemotePermissionEvaluator implements PermissionEvaluator {

        private final WebClient authWebClient;
        private final RoleLookupService roleLookupService;

        // Roles that have full access
        private static final Set<String> ADMIN_ROLES = Set.of("admin", "super-admin");

        // Roles that can read anything
        private static final Set<String> READ_ROLES = Set.of("admin", "super-admin", "editor", "viewer");

        // Roles that can write (create/update/delete)
        private static final Set<String> WRITE_ROLES = Set.of("admin", "super-admin", "editor");

        @Override
        @Cacheable(value = CacheNames.PERMISSIONS, key = "#userId + ':' + #resource + ':' + #action")
        public boolean hasPermission(String userId, String resource, String action) {
                String tenantId = TenantContext.getCurrentTenant();

                // Look up role from database via RoleLookupService
                String role = roleLookupService.getUserRole(userId, tenantId).orElse(null);
                log.debug("Checking permission: user={}, resource={}, action={}, role={}, tenant={}",
                                userId, resource, action, role, tenantId);

                // Admin/Super-admin bypass - full access to everything
                if (role != null && ADMIN_ROLES.contains(role)) {
                        log.debug("Admin bypass: user={}, role={}", userId, role);
                        return true;
                }

                // Role-based access check for simplified model
                if (role != null) {
                        boolean allowed = checkRoleBasedAccess(role, action);
                        if (allowed) {
                                log.debug("Role-based access granted: user={}, role={}, action={}", userId, role,
                                                action);
                                return true;
                        }
                }

                // Fallback to remote auth-service check for complex cases
                return checkRemotePermission(userId, resource, action, tenantId);
        }

        /**
         * Check access based on role and action type.
         * Simplified model: admin=all, editor=read+write, viewer=read, guest=minimal
         */
        private boolean checkRoleBasedAccess(String role, String action) {
                // Read actions
                if ("read".equals(action) || "view".equals(action) || "list".equals(action)) {
                        return READ_ROLES.contains(role);
                }
                // Write actions
                if ("create".equals(action) || "update".equals(action) || "delete".equals(action) ||
                                "write".equals(action) || "edit".equals(action)) {
                        return WRITE_ROLES.contains(role);
                }
                // Share action - editor and above
                if ("share".equals(action)) {
                        return WRITE_ROLES.contains(role);
                }
                return false;
        }

        /**
         * Fallback to remote auth-service permission check.
         * Used for complex permission rules that can't be resolved by role alone.
         */
        private boolean checkRemotePermission(String userId, String resource, String action, String tenantId) {
                log.debug("Falling back to remote permission check: user={}, resource={}, action={}",
                                userId, resource, action);

                try {
                        WebClient.RequestBodySpec request = authWebClient.post()
                                        .uri("/auth/api/v1/permissions/check")
                                        .contentType(MediaType.APPLICATION_JSON);

                        // Pass tenant context to auth-service for DB routing
                        if (tenantId != null && !tenantId.isBlank()) {
                                request = request.header("X-Tenant-Id", tenantId);
                        }

                        Boolean allowed = request
                                        .bodyValue(Map.of(
                                                        "userId", userId,
                                                        "resource", resource,
                                                        "action", action))
                                        .retrieve()
                                        .bodyToMono(Boolean.class)
                                        .block();

                        boolean result = Boolean.TRUE.equals(allowed);
                        log.debug("Remote permission check result: user={}, resource={}, action={}, allowed={}",
                                        userId, resource, action, result);
                        return result;

                } catch (Exception e) {
                        log.error("Remote permission check failed: user={}, resource={}, action={}, error={}",
                                        userId, resource, action, e.getMessage());
                        // Fail closed: deny access on error
                        return false;
                }
        }
}
