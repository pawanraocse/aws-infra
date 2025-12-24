package com.learning.common.infra.cache;

import java.util.Set;

/**
 * Centralized cache name constants.
 * All services use these constants to avoid name mismatches.
 * 
 * <h2>Distribution Strategy</h2>
 * <ul>
 * <li><b>DISTRIBUTED</b> caches use Redis - shared across all instances</li>
 * <li><b>LOCAL</b> caches use Caffeine - per-instance, no network overhead</li>
 * </ul>
 * 
 * <h2>When to use DISTRIBUTED</h2>
 * <ul>
 * <li>User sessions/permissions (must be consistent across pods)</li>
 * <li>Tokens, rate limits, locks</li>
 * </ul>
 * 
 * <h2>When to use LOCAL</h2>
 * <ul>
 * <li>Configuration that rarely changes</li>
 * <li>Static reference data</li>
 * <li>Per-request caching</li>
 * </ul>
 * 
 * <p>
 * To add a new cache:
 * </p>
 * <ol>
 * <li>Add constant here</li>
 * <li>Add to DISTRIBUTED_CACHES or LOCAL_CACHES set</li>
 * <li>Register in CommonCacheConfiguration</li>
 * </ol>
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

    /**
     * [DISTRIBUTED] Cache for user roles lookup.
     * Key: userId:tenantId
     * Value: Optional<String> (role ID)
     * TTL: 10 minutes
     */
    public static final String USER_ROLES = "userRoles";

    // === Tenant Caches (common-infra) ===

    /**
     * [LOCAL] Cache for tenant DB connection info.
     * Key: tenantId
     * Value: TenantDbInfo
     * TTL: 10 minutes
     * Note: OK to be local since tenant config rarely changes
     */
    public static final String TENANT_CONFIG = "tenantConfig";

    // === Cache Sets for Routing ===

    /**
     * Caches that require distributed storage (Redis).
     * These must be consistent across all service instances.
     */
    public static final Set<String> DISTRIBUTED_CACHES = Set.of(
            PERMISSIONS,
            USER_PERMISSIONS,
            USER_ALL_PERMISSIONS,
            USER_ROLES);

    /**
     * Caches that can be local (Caffeine).
     * These don't need cross-instance consistency.
     */
    public static final Set<String> LOCAL_CACHES = Set.of(
            TENANT_CONFIG);

    /**
     * Check if a cache should use distributed storage.
     *
     * @param cacheName the cache name
     * @return true if distributed (Redis), false if local (Caffeine)
     */
    public static boolean isDistributed(String cacheName) {
        return DISTRIBUTED_CACHES.contains(cacheName);
    }

    /**
     * Get all cache names.
     *
     * @return set of all cache names
     */
    public static Set<String> all() {
        return Set.of(PERMISSIONS, USER_PERMISSIONS, USER_ALL_PERMISSIONS, USER_ROLES, TENANT_CONFIG);
    }

    private CacheNames() {
        // Prevent instantiation
    }
}
