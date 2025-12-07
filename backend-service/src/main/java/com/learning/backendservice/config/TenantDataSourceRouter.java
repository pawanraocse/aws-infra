package com.learning.backendservice.config;

import com.learning.backendservice.tenant.registry.TenantRegistryService;
import com.learning.common.dto.TenantDbConfig;
import com.learning.common.infra.tenant.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
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

    private final TenantRegistryService tenantRegistry;
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final DataSource defaultDataSource;

    public TenantDataSourceRouter(TenantRegistryService tenantRegistry) {
        this(tenantRegistry, null);
    }

    public TenantDataSourceRouter(TenantRegistryService tenantRegistry, DataSource defaultDataSource) {
        this.tenantRegistry = tenantRegistry;
        this.defaultDataSource = defaultDataSource;
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
            log.debug("No tenant context found, using default data source");
            return null;
        }
        log.debug("Routing to tenant database: {}", tenantId);
        return tenantId;
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null) {
            log.debug("No tenant context set, using default datasource");
            if (defaultDataSource == null) {
                throw new IllegalStateException("No tenant context and no default datasource configured");
            }
            return defaultDataSource;
        }

        // Get or create data source for this tenant
        return tenantDataSources.computeIfAbsent(tenantId, this::createTenantDataSource);
    }

    private DataSource createTenantDataSource(String tenantId) {
        log.info("Creating new data source for tenant: {}", tenantId);
        try {
            TenantDbConfig config = tenantRegistry.load(tenantId);
            log.info("Loaded tenant DB config: jdbcUrl={}, username={}", config.jdbcUrl(), config.username());

            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(config.jdbcUrl());
            dataSource.setUsername(config.username());
            dataSource.setPassword(config.password());
            dataSource.setMaximumPoolSize(5);
            dataSource.setMinimumIdle(1);
            dataSource.setConnectionTimeout(30000);
            dataSource.setIdleTimeout(600000);
            dataSource.setMaxLifetime(1800000);
            dataSource.setPoolName("tenant-" + tenantId);

            log.info("Successfully created data source for tenant: {} with URL: {}", tenantId, config.jdbcUrl());
            return dataSource;
        } catch (Throwable e) {
            log.error("Failed to create data source for tenant: {} - {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to create tenant data source", e);
        }
    }

    /**
     * Evict a tenant's data source from cache.
     */
    public void evictTenantDataSource(String tenantId) {
        DataSource ds = tenantDataSources.remove(tenantId);
        if (ds instanceof HikariDataSource hikari) {
            hikari.close();
            log.info("Evicted and closed data source for tenant: {}", tenantId);
        }
    }
}
