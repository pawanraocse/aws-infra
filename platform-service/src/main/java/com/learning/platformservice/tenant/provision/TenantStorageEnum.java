package com.learning.platformservice.tenant.provision;

import java.util.Locale;

/**
 * Tenant storage modes for multi-tenancy.
 * 
 * <ul>
 * <li>{@link #DATABASE} - Per-tenant database (organizations)</li>
 * <li>{@link #SHARED} - Shared database with tenant_id filtering (personal)</li>
 * </ul>
 */
public enum TenantStorageEnum {
    /**
     * Each tenant gets a dedicated PostgreSQL database.
     * Used for: Organization tenants
     */
    DATABASE,
    
    /**
     * Tenants share a single database with row-level tenant_id filtering.
     * Used for: Personal tenants
     */
    SHARED;

    public static TenantStorageEnum fromString(String mode) {
        if (mode == null) throw new IllegalArgumentException("Storage mode cannot be null");
        try {
            return TenantStorageEnum.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown storage mode: " + mode, e);
        }
    }
}
