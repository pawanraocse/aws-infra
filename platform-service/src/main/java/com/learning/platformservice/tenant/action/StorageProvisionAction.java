package com.learning.platformservice.tenant.action;

import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Responsible only for physical storage creation (schema / database) and jdbcUrl assignment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class StorageProvisionAction implements TenantProvisionAction {

    private final TenantProvisioner tenantProvisioner;

    @Override
    public void execute(TenantProvisionContext context) throws TenantProvisioningException {
        try {
            String jdbcUrl = tenantProvisioner.provisionTenantStorage(context.getTenant().getId(), context.getRequest().storageMode());
            context.setJdbcUrl(jdbcUrl);
        } catch (Exception e) {
            log.error("storage_provision_failed tenantId={} error={}", context.getTenant().getId(), e.getMessage(), e);
            throw new TenantProvisioningException(context.getTenant().getId(), "Storage provision failed: " + e.getMessage(), e);
        }
    }
}

