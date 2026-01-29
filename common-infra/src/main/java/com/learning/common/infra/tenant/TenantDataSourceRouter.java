package com.learning.common.infra.tenant;

import com.learning.common.dto.TenantDbConfig;
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
 * 
 * Supports two storage modes:
 * <ul>
 * <li>DATABASE: Per-tenant dedicated database (organizations)</li>
 * <li>SHARED: Shared personal database with tenant_id filtering (personal users)</li>
 * </ul>
 */
@Slf4j
public class TenantDataSourceRouter extends AbstractRoutingDataSource {

    /**
     * Special tenant ID for super-admin users.
     * Super-admins use the default/platform datasource.
     */
    public static final String SYSTEM_TENANT_ID = "system";

    private final TenantRegistryService tenantRegistry;
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final DataSource defaultDataSource;
    private final DataSource personalSharedDataSource;

    public TenantDataSourceRouter(TenantRegistryService tenantRegistry) {
        this(tenantRegistry, null, null);
    }

    public TenantDataSourceRouter(TenantRegistryService tenantRegistry, DataSource defaultDataSource) {
        this(tenantRegistry, defaultDataSource, null);
    }

    public TenantDataSourceRouter(TenantRegistryService tenantRegistry, 
                                   DataSource defaultDataSource,
                                   DataSource personalSharedDataSource) {
        this.tenantRegistry = tenantRegistry;
        this.defaultDataSource = defaultDataSource;
        this.personalSharedDataSource = personalSharedDataSource;
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

        // No tenant context - use default
        if (tenantId == null) {
            log.debug("TenantDataSourceRouter: No tenant context, using default datasource");
            if (defaultDataSource == null) {
                throw new IllegalStateException("No tenant context and no default datasource configured");
            }
            return defaultDataSource;
        }

        // System tenant (super-admin) - use default/platform datasource
        if (SYSTEM_TENANT_ID.equals(tenantId)) {
            log.debug("TenantDataSourceRouter: System tenant, using default datasource");
            if (defaultDataSource == null) {
                throw new IllegalStateException("System tenant requires default datasource but none configured");
            }
            return defaultDataSource;
        }

        // Check if tenant uses SHARED storage mode (personal tenants)
        TenantDbConfig config = tenantRegistry.load(tenantId);
        if (config != null && "SHARED".equals(config.storageMode())) {
            if (personalSharedDataSource != null) {
                log.debug("TenantDataSourceRouter: SHARED tenant {}, using personal shared datasource", tenantId);
                return personalSharedDataSource;
            } else {
                log.warn("TenantDataSourceRouter: SHARED tenant {} but no personalSharedDataSource configured", tenantId);
            }
        }

        // DATABASE mode: Get or create per-tenant data source
        log.debug("TenantDataSourceRouter: DATABASE tenant {}, using dedicated datasource", tenantId);
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

    /**
     * Get count of cached tenant data sources.
     */
    public int getActiveTenantCount() {
        return tenantDataSources.size();
    }
}
