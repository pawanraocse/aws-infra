package com.learning.platformservice.tenant.action;

import com.learning.common.dto.MigrationResult;
import com.learning.common.dto.TenantDbConfig;
import com.learning.common.util.SimpleCryptoUtil;
import com.learning.platformservice.tenant.action.migration.AuthServiceMigration;
import com.learning.platformservice.tenant.action.migration.BackendServiceMigration;
import com.learning.platformservice.tenant.action.migration.ServiceMigrationStrategy;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantStorageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Invokes downstream service migrations (phase 2 of provisioning) after storage
 * is created.
 * 
 * For SHARED mode (personal tenants): Skips per-tenant migrations (schema exists in shared DB).
 * For DATABASE mode (org tenants): Executes migrations on dedicated tenant DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(50)
public class MigrationInvokeAction implements TenantProvisionAction {

    private final WebClient backendWebClient;
    private final WebClient authWebClient;

    @Override
    public void execute(TenantProvisionContext context) throws TenantProvisioningException {
        String tenantId = context.getTenant().getId();
        String storageMode = context.getTenant().getStorageMode();

        // Skip migrations for SHARED mode - schema already exists in personal_shared DB
        if (TenantStorageEnum.SHARED.name().equals(storageMode)) {
            log.info("migration_skipped_shared_mode tenantId={}", tenantId);
            return;
        }

        // DATABASE mode: execute per-tenant migrations
        TenantDbConfig dbConfig = buildDbConfig(context);

        List<ServiceMigrationStrategy> strategies = List.of(
                new BackendServiceMigration(backendWebClient),
                new AuthServiceMigration(authWebClient));

        String lastVersion = null;
        for (ServiceMigrationStrategy strategy : strategies) {
            try {
                MigrationResult result = strategy.migrate(tenantId, dbConfig);
                if (result != null) {
                    lastVersion = result.lastVersion();
                    log.info("tenant_migration_success service={} tenant={} version={}",
                            strategy.serviceName(), tenantId, lastVersion);

                    // If auth-service created an OpenFGA store, save the ID
                    if (result.fgaStoreId() != null) {
                        log.info("Saving OpenFGA store ID for tenant {}: {}", tenantId, result.fgaStoreId());
                        context.getTenant().setFgaStoreId(result.fgaStoreId());
                    }
                }
            } catch (Exception e) {
                log.error("tenant_migration_failed service={} tenant={} error={}",
                        strategy.serviceName(), tenantId, e.getMessage());
                throw new TenantProvisioningException(
                        tenantId,
                        strategy.serviceName() + " migration failed: " + e.getMessage(),
                        e);
            }
        }

        context.setLastMigrationVersion(lastVersion);
    }

    private TenantDbConfig buildDbConfig(TenantProvisionContext context) {
        Tenant tenant = context.getTenant();
        String decryptedPassword = SimpleCryptoUtil.decrypt(tenant.getDbUserPasswordEnc());
        return new TenantDbConfig(
                tenant.getJdbcUrl(),
                tenant.getDbUserSecretRef(),
                decryptedPassword);
    }
}
