package com.learning.platformservice.tenant.service;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;

public interface TenantProvisioningService {
    TenantDto provision(ProvisionTenantRequest request);

    // Future: retry migration of failed tenant services
    TenantDto retryMigration(String tenantId);

    /**
     * Delete/Deprovision a tenant.
     * 
     * @param tenantId The ID of the tenant to delete.
     */
    void deprovision(String tenantId);
}
