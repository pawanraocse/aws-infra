package com.learning.common.infra.cache;

/**
 * Centralized cache name constants.
 * All services use these constants to avoid name mismatches.
 * 
 * DISTRIBUTION NOTES:
 * - Caffeine = LOCAL in-memory cache (not distributed)
 * - For multi-instance deployments, caches marked [DISTRIBUTED] should use
 * Redis
 * - See roadmap for Redis integration task
 * 
 * To add a new cache:
 * 1. Add constant here
 * 2. Register in CommonCacheConfiguration
 */
public final class CacheNames {

    // === Permission Caches (auth-service) ===

    /**
     * [DISTRIBUTED] Cache for individual permission checks.
     * Key: userId:resource:action
     * Value: Boolean
     * TTL: 10 minutes
     */
    public static final String PERMISSIONS = "permissions";

    /**
     * [DISTRIBUTED] Cache for user's permission list.
     * Key: userId
     * Value: List<Permission>
     * TTL: 10 minutes
     */
    public static final String USER_PERMISSIONS = "userPermissions";

    /**
     * [DISTRIBUTED] Cache for user's all permissions.
     * Key: userId
     * Value: Set<String> (permission strings)
     * TTL: 10 minutes
     */
    public static final String USER_ALL_PERMISSIONS = "userAllPermissions";

    // === Tenant Caches (common-infra) ===

    /**
     * [LOCAL] Cache for tenant DB connection info.
     * Key: tenantId
     * Value: TenantDbInfo
     * TTL: 10 minutes
     * Note: OK to be local since tenant config rarely changes
     */
    public static final String TENANT_CONFIG = "tenantConfig";

    private CacheNames() {
        // Prevent instantiation
    }
}
