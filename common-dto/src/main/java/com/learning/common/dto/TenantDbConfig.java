package com.learning.common.dto;

/**
 * Tenant database and OpenFGA configuration.
 * Used by TenantRegistryService to cache tenant-specific settings.
 */
public record TenantDbConfig(
        String jdbcUrl,
        String username,
        String password,
        String fgaStoreId,   // OpenFGA store ID for this tenant (null if not enabled)
        String storageMode   // DATABASE or SHARED
) {
    /**
     * Constructor without fgaStoreId and storageMode for backward compatibility.
     */
    public TenantDbConfig(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, null, "DATABASE");
    }

    /**
     * Constructor without storageMode for backward compatibility.
     */
    public TenantDbConfig(String jdbcUrl, String username, String password, String fgaStoreId) {
        this(jdbcUrl, username, password, fgaStoreId, "DATABASE");
    }
}
