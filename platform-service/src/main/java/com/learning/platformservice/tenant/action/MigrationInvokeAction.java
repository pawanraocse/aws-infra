package com.learning.platformservice.tenant.action;

import com.learning.common.dto.MigrationResult;
import com.learning.common.dto.TenantDbConfig;
import com.learning.common.util.SimpleCryptoUtil;
import com.learning.platformservice.tenant.action.migration.AuthServiceMigration;
import com.learning.platformservice.tenant.action.migration.BackendServiceMigration;
import com.learning.platformservice.tenant.action.migration.ServiceMigrationStrategy;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Invokes downstream service migrations (phase 2 of provisioning) after storage
 * is created.
 * Uses Strategy Pattern to orchestrate migrations across multiple services.
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

        // Build tenant DB config to share with services
        TenantDbConfig dbConfig = buildDbConfig(context);

        // Service migration strategies
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
        // Decrypt password before sending to internal services
        // Note: In a real mesh, we might pass the secret ref, but here we pass
        // credentials
        String decryptedPassword = SimpleCryptoUtil.decrypt(tenant.getDbUserPasswordEnc());
        return new TenantDbConfig(
                tenant.getJdbcUrl(),
                tenant.getDbUserSecretRef(),
                decryptedPassword);
    }

    // Backend service strategy


    // Auth service strategy

}
