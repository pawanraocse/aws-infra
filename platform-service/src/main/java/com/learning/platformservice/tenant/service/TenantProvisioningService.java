package com.learning.platformservice.tenant.service;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;

public interface TenantProvisioningService {
    TenantDto provision(ProvisionTenantRequest request);

    /**
     * Initialize tenant row with PROVISIONING status (no actions executed).
     * Used by async provisioning: auth-service calls this first, then sends SQS message.
     */
    TenantDto initTenant(ProvisionTenantRequest request);

    /**
     * Execute provisioning actions (storage, migration, audit) on an existing tenant row.
     * Used by SQS consumer for async provisioning.
     */
    TenantDto executeProvisioningActions(String tenantId);

    // Future: retry migration of failed tenant services
    TenantDto retryMigration(String tenantId);

    /**
     * Delete/Deprovision a tenant.
     * 
     * @param tenantId The ID of the tenant to delete.
     */
    void deprovision(String tenantId);
}
