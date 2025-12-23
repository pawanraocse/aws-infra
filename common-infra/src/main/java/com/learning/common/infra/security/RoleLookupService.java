package com.learning.common.infra.security;

import java.util.Optional;

/**
 * Interface for looking up user roles.
 * Implementations can query auth-service or use cached data.
 * 
 * <p>
 * This replaces the X-Role header approach where roles were
 * passed from gateway. Now services look up roles directly.
 * </p>
 */
public interface RoleLookupService {

    /**
     * Get the primary role for a user.
     * 
     * @param userId   Cognito user ID (from X-User-Id header)
     * @param tenantId Tenant ID for context (from X-Tenant-Id header)
     * @return Role ID (e.g., "admin", "editor", "viewer", "super-admin") or empty
     *         if not found
     */
    Optional<String> getUserRole(String userId, String tenantId);

    /**
     * Check if user has super-admin role.
     * Super-admins have access to all resources.
     * 
     * @param userId   Cognito user ID
     * @param tenantId Tenant ID for context
     * @return true if user is super-admin
     */
    default boolean isSuperAdmin(String userId, String tenantId) {
        return getUserRole(userId, tenantId)
                .map(role -> "super-admin".equals(role))
                .orElse(false);
    }

    /**
     * Check if user has admin-level access.
     * 
     * @param userId   Cognito user ID
     * @param tenantId Tenant ID
     * @return true if user has admin or super-admin role
     */
    default boolean hasAdminAccess(String userId, String tenantId) {
        return getUserRole(userId, tenantId)
                .map(role -> "admin".equals(role) || "super-admin".equals(role))
                .orElse(false);
    }
}
