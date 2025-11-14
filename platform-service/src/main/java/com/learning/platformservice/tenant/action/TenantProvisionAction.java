package com.learning.platformservice.tenant.action;

import com.learning.platformservice.tenant.exception.TenantProvisioningException;

/**
 * Single responsibility action executed during tenant provisioning.
 * Implementations should NOT change tenant status directly except for failure handling where they can throw.
 */
public interface TenantProvisionAction {
    /**
     * Execute action logic; may mutate context enrichment fields.
     */
    void execute(TenantProvisionContext context) throws TenantProvisioningException;
}

