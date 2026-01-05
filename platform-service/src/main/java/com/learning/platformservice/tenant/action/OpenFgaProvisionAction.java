package com.learning.platformservice.tenant.action;

import com.learning.common.infra.openfga.OpenFgaStoreService;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Provision OpenFGA store for tenant during signup.
 * Only active when openfga.enabled=true.
 * 
 * This action runs AFTER storage provisioning (@Order 15, storage is @Order 10)
 * to ensure the tenant is properly set up before creating the OpenFGA store.
 * 
 * What it does:
 * 1. Creates a dedicated OpenFGA store for this tenant
 * 2. Stores the store ID in the tenant record
 * 3. The store will be used for all fine-grained permission checks
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(15) // After StorageProvisionAction (10), before MigrationInvokeAction (20)
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaProvisionAction implements TenantProvisionAction {

    private final OpenFgaStoreService openFgaStoreService;

    @Override
    public void execute(TenantProvisionContext context) throws TenantProvisioningException {
        String tenantId = context.getTenant().getId();
        String tenantName = context.getTenant().getName();

        try {
            log.info("Creating OpenFGA store for tenant: {} ({})", tenantId, tenantName);

            String storeId = openFgaStoreService.createStoreForTenant(tenantId, tenantName);

            if (storeId != null) {
                // Store the OpenFGA store ID in the tenant record
                context.getTenant().setFgaStoreId(storeId);
                log.info("✅ OpenFGA store created for tenant {}: {}", tenantId, storeId);
            } else {
                log.warn("OpenFGA store creation skipped or failed for tenant: {}", tenantId);
            }

        } catch (Exception e) {
            // Log error but don't fail provisioning - OpenFGA is optional
            log.error("OpenFGA store creation failed for tenant {}: {}", tenantId, e.getMessage(), e);
            // Not throwing TenantProvisioningException to avoid failing the whole signup
        }
    }
}
