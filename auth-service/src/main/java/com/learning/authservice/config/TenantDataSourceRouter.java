package com.learning.authservice.config;

import com.learning.authservice.tenant.AuthServiceTenantRegistry;
import com.learning.common.dto.TenantDbConfig;
import com.learning.common.infra.tenant.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes database connections to tenant-specific databases based on
 * TenantContext.
 * Dynamically creates and caches data source connections for each tenant.
 */
@Slf4j
public class TenantDataSourceRouter extends AbstractRoutingDataSource {

    private final AuthServiceTenantRegistry tenantRegistry;
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final DataSource defaultDataSource; // For test environments and startup

    public TenantDataSourceRouter(AuthServiceTenantRegistry tenantRegistry) {
        this(tenantRegistry, null);
    }

    public TenantDataSourceRouter(AuthServiceTenantRegistry tenantRegistry, DataSource defaultDataSource) {
        this.tenantRegistry = tenantRegistry;
        this.defaultDataSource = defaultDataSource; // Store for direct access
        this.setTargetDataSources(java.util.Collections.emptyMap());
        if (defaultDataSource != null) {
            this.setDefaultTargetDataSource(defaultDataSource);
        }
        this.afterPropertiesSet();
    }

    @Override
    @Nullable
    protected Object determineCurrentLookupKey() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.trace("No tenant context found, using default data source");
            return null;
        }
        log.trace("Routing to tenant database: {}", tenantId);
        return tenantId;
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null) {
            // During application startup or when no tenant context is set,
            // return the default datasource directly (for test environments).
            log.trace("No tenant context set, using default datasource");
            return defaultDataSource; // Return stored default (may be null in production)
        }

        // Get or create data source for this tenant
        return tenantDataSources.computeIfAbsent(tenantId, this::createTenantDataSource);
    }

    private DataSource createTenantDataSource(String tenantId) {
        log.info("Creating new data source for tenant: {}", tenantId);

        TenantDbConfig config = tenantRegistry.load(tenantId);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.jdbcUrl());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());
        dataSource.setMaximumPoolSize(5); // Smaller pool per tenant
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setPoolName("tenant-" + tenantId);

        log.info("Successfully created data source for tenant: {} with URL: {}", tenantId, config.jdbcUrl());
        return dataSource;
    }

    /**
     * Evict a tenant's data source from cache (for testing or tenant database
     * changes).
     */
    public void evictTenantDataSource(String tenantId) {
        DataSource ds = tenantDataSources.remove(tenantId);
        if (ds instanceof HikariDataSource hikari) {
            hikari.close();
            log.info("Evicted and closed data source for tenant: {}", tenantId);
        }
    }
}
