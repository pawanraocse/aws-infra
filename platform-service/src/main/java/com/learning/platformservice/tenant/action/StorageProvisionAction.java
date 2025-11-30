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
 * Responsible only for physical storage creation (schema / database) and
 * jdbcUrl assignment.
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
            String jdbcUrl = tenantProvisioner.provisionTenantStorage(tenantId, mode);
            context.setJdbcUrl(jdbcUrl);
            context.getTenant().setJdbcUrl(jdbcUrl); // Also set on tenant entity for migrations

            String dbUsername = ("%s_user".formatted(tenantId)).toLowerCase().replace("-", "_");
            String schemaName = tenantProvisioner.buildDatabaseName(tenantId);

            var identifiers = resolveIdentifiers(mode, jdbcUrl, schemaName);
            String identifierForGrants = identifiers.identifierForGrants();

            String rawPassword = tenantProvisioner.createTenantDbUser(
                    identifierForGrants, dbUsername, mode);

            context.getTenant().setDbUserSecretRef(dbUsername);
            context.getTenant().setDbUserPasswordEnc(
                    SimpleCryptoUtil.encrypt(rawPassword));

        } catch (Exception e) {
            log.error("storage_provision_failed tenantId={} error={}", tenantId, e.getMessage(), e);
            throw new TenantProvisioningException(
                    tenantId, "Storage provision failed: " + e.getMessage(), e);
        }
    }

    private TenantIdentifiers resolveIdentifiers(
            TenantStorageEnum mode,
            String jdbcUrl,
            String schemaName) {
        return switch (mode) {
            case DATABASE -> new TenantIdentifiers(schemaName, schemaName);

            case SCHEMA -> {
                String sharedDb = extractDatabaseName(jdbcUrl);
                yield new TenantIdentifiers(sharedDb, schemaName);
            }
        };
    }

    private String extractDatabaseName(String jdbcUrl) {
        // jdbc:postgresql://host:port/dbName?params
        String withoutProtocol = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2); // host:port/db?params
        String[] parts = withoutProtocol.split("/", 2); // ["host:port", "db?params"]
        String dbAndParams = parts[1];

        int idx = dbAndParams.indexOf("?");
        return (idx == -1) ? dbAndParams : dbAndParams.substring(0, idx);
    }

}
