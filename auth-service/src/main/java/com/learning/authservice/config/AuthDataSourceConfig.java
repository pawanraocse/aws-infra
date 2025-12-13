package com.learning.authservice.config;

import com.learning.common.infra.tenant.PlatformServiceTenantRegistry;
import com.learning.common.infra.tenant.TenantDataSourceRouter;
import com.learning.common.infra.tenant.TenantLocalCache;
import com.learning.common.infra.tenant.TenantRegistryService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;

/**
 * Configures tenant-aware data sources for auth-service.
 * Uses shared infrastructure from common-infra for multi-tenant routing.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.learning.authservice", entityManagerFactoryRef = "tenantEntityManagerFactory", transactionManagerRef = "tenantTransactionManager")
@Slf4j
public class AuthDataSourceConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/awsinfra}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username:postgres}")
    private String defaultUsername;

    @Value("${spring.datasource.password:postgres}")
    private String defaultPassword;

    /**
     * Local cache for tenant DB configs.
     */
    @Bean
    public TenantLocalCache tenantLocalCache() {
        return new TenantLocalCache();
    }

    /**
     * Tenant registry that fetches config from platform-service.
     */
    @Bean
    public TenantRegistryService tenantRegistryService(
            @Qualifier("platformWebClient") WebClient platformWebClient,
            TenantLocalCache tenantLocalCache) {
        return new PlatformServiceTenantRegistry(platformWebClient, tenantLocalCache);
    }

    /**
     * Default DataSource for health checks and when no tenant context is set.
     */
    @Bean(name = "defaultDataSource")
    public DataSource defaultDataSource() {
        log.info("Configuring default data source: {}", defaultJdbcUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(defaultJdbcUrl);
        dataSource.setUsername(defaultUsername);
        dataSource.setPassword(defaultPassword);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setPoolName("auth-default-pool");

        return dataSource;
    }

    /**
     * Tenant DataSource Router - dynamically routes to tenant databases.
     */
    @Bean(name = "tenantDataSource")
    @Primary
    public DataSource tenantDataSource(
            TenantRegistryService tenantRegistryService,
            @Qualifier("defaultDataSource") DataSource defaultDataSource) {
        log.info("Configuring tenant data source router for auth-service");
        return new TenantDataSourceRouter(tenantRegistryService, defaultDataSource);
    }

    /**
     * EntityManagerFactory for tenant-specific entities.
     */
    @Bean(name = "tenantEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("tenantDataSource") DataSource tenantDataSource) {

        log.info("Configuring tenant EntityManagerFactory for auth-service");

        return builder
                .dataSource(tenantDataSource)
                .packages("com.learning.authservice")
                .persistenceUnit("tenant")
                .properties(java.util.Map.of(
                        "hibernate.hbm2ddl.auto", "none",
                        "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"))
                .build();
    }

    /**
     * Transaction manager for tenant entities.
     */
    @Bean(name = "tenantTransactionManager")
    @Primary
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEntityManagerFactory) {

        return new JpaTransactionManager(tenantEntityManagerFactory);
    }
}
