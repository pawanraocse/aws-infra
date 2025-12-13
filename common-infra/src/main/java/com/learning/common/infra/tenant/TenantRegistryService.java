package com.learning.common.infra.tenant;

import com.learning.common.dto.TenantDbConfig;

/**
 * Interface for loading tenant database configurations.
 * Implementations fetch tenant DB connection info from a registry (e.g.,
 * platform-service).
 */
public interface TenantRegistryService {

    /**
     * Load the database configuration for a tenant.
     * 
     * @param tenantId The tenant identifier
     * @return TenantDbConfig with JDBC URL, username, and password
     * @throws IllegalArgumentException if tenant not found
     */
    TenantDbConfig load(String tenantId);
}
