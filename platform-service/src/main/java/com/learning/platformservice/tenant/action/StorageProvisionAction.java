package com.learning.platformservice.tenant.action;

import com.learning.common.util.SimpleCryptoUtil;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.provision.TenantStorageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Responsible for physical storage creation (database) and jdbcUrl assignment.
 * 
 * For SHARED mode (personal tenants): Uses pre-configured shared DB URL, no physical storage created.
 * For DATABASE mode (org tenants): Creates dedicated database and tenant DB user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class StorageProvisionAction implements TenantProvisionAction {

    private final TenantProvisioner tenantProvisioner;

    @Override
    public void execute(TenantProvisionContext context) throws TenantProvisioningException {

        String tenantId = context.getTenant().getId();
        TenantStorageEnum mode = TenantStorageEnum.fromString(context.getRequest().storageMode());

        try {
            // Get JDBC URL (for SHARED: returns shared DB URL, for DATABASE: creates new DB)
            String jdbcUrl = tenantProvisioner.provisionTenantStorage(tenantId, mode);
            context.setJdbcUrl(jdbcUrl);
            context.getTenant().setJdbcUrl(jdbcUrl);
            context.getTenant().setStorageMode(mode.name());

            // For SHARED mode: skip DB user creation (use shared connection pool)
            if (mode == TenantStorageEnum.SHARED) {
                log.info("storage_provision_shared tenantId={} jdbcUrl={}", tenantId, jdbcUrl);
                return;
            }

            // For DATABASE mode: create dedicated DB user with credentials
            String dbUsername = ("%s_user".formatted(tenantId)).toLowerCase().replace("-", "_");
            String schemaName = tenantProvisioner.buildDatabaseName(tenantId);

            String identifierForGrants = schemaName; // For DATABASE mode, schema name = DB name

            String rawPassword = tenantProvisioner.createTenantDbUser(
                    identifierForGrants, dbUsername, mode);

            context.getTenant().setDbUserSecretRef(dbUsername);
            context.getTenant().setDbUserPasswordEnc(
                    SimpleCryptoUtil.encrypt(rawPassword));

            log.info("storage_provision_database tenantId={} dbName={}", tenantId, schemaName);

        } catch (Exception e) {
            log.error("storage_provision_failed tenantId={} error={}", tenantId, e.getMessage(), e);
            throw new TenantProvisioningException(
                    tenantId, "Storage provision failed: " + e.getMessage(), e);
        }
    }
}
