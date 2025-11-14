package com.learning.platformservice.tenant.provision;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Component
@Slf4j
public class TenantProvisioner {
    private final JdbcTemplate jdbcTemplate;
    private final boolean databaseModeEnabled;

    public TenantProvisioner(DataSource dataSource,
                             @Value("${platform.tenant.database-mode.enabled:false}") boolean databaseModeEnabled) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.databaseModeEnabled = databaseModeEnabled;
    }

    @Transactional
    public String provisionTenantStorage(String tenantId, String storageMode) {
        String sanitized = sanitize(tenantId);
        if ("SCHEMA".equalsIgnoreCase(storageMode)) {
            String schemaName = "tenant_" + sanitized;
            log.debug("create_schema schema={}", schemaName);
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            return "jdbc:postgresql://localhost:5432/awsinfra?currentSchema=" + schemaName;
        } else if ("DATABASE".equalsIgnoreCase(storageMode)) {
            if (!databaseModeEnabled) {
                throw new IllegalStateException("DATABASE mode disabled by feature flag");
            }
            String dbName = "tenant_" + sanitized;
            log.debug("create_database db={}", dbName);
            jdbcTemplate.execute("CREATE DATABASE " + dbName);
            return "jdbc:postgresql://localhost:5432/" + dbName;
        } else {
            throw new IllegalArgumentException("Unknown storage mode: " + storageMode);
        }
    }

    private String sanitize(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    }
}
