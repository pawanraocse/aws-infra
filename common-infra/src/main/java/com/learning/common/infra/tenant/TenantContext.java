package com.learning.common.infra.tenant;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadLocal storage for the current Tenant ID.
 * Used to propagate tenant context throughout the request lifecycle.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    /**
     * Set the current tenant ID.
     * 
     * @param tenantId Tenant ID
     */
    public static void setCurrentTenant(String tenantId) {
        log.trace("Setting tenant context: {}", tenantId);
        currentTenant.set(tenantId);
    }

    /**
     * Get the current tenant ID.
     * 
     * @return Tenant ID or null if not set
     */
    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    /**
     * Clear the current tenant context.
     * Should be called at the end of the request.
     */
    public static void clear() {
        log.trace("Clearing tenant context");
        currentTenant.remove();
    }
}
