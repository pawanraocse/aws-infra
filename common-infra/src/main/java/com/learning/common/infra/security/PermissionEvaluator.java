package com.learning.common.infra.security;

/**
 * Interface for evaluating permissions.
 * Implementations will define how permissions are checked (e.g., local DB
 * lookup or remote API call).
 * Tenant context is implicit via TenantDataSourceRouter - all operations run
 * against the current tenant's database.
 */
public interface PermissionEvaluator {

    /**
     * Check if the current user has the required permission.
     *
     * @param userId   User ID
     * @param resource Resource name
     * @param action   Action name
     * @return true if allowed, false otherwise
     */
    boolean hasPermission(String userId, String resource, String action);
}
