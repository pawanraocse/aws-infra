package com.learning.authservice.config;

import com.learning.authservice.tenant.AuthServiceTenantRegistry;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
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

import javax.sql.DataSource;

/**
 * Configures dual data sources for multi-tenant architecture:
 * 1. Platform DataSource - Connects to shared 'awsinfra' database for
 * platform-level tables
 * 2. Tenant DataSource - Routes to tenant-specific databases based on
 * TenantContext
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {
        "com.learning.authservice.authorization.repository",
        "com.learning.authservice.invitation.repository"
}, entityManagerFactoryRef = "tenantEntityManagerFactory", transactionManagerRef = "tenantTransactionManager")
@RequiredArgsConstructor
@Slf4j
public class DataSourceConfig {

    @Value("${app.datasource.platform.url}")
    private String platformJdbcUrl;

    @Value("${app.datasource.platform.username}")
    private String platformUsername;

    @Value("${app.datasource.platform.password}")
    private String platformPassword;

    private final AuthServiceTenantRegistry tenantRegistry;

    /**
     * Platform DataSource for shared tables (tenant registry, audit logs, usage
     * metrics).
     * Connects to the 'awsinfra' database.
     */
    @Bean(name = "platformDataSource")
    public DataSource platformDataSource() {
        log.info("Configuring platform data source: {}", platformJdbcUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(platformJdbcUrl);
        dataSource.setUsername(platformUsername);
        dataSource.setPassword(platformPassword);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setPoolName("platform-pool");

        return dataSource;
    }

    /**
     * Tenant DataSource Router for tenant-specific tables.
     * Dynamically routes to tenant databases based on TenantContext.
     */
    @Bean(name = "tenantDataSource")
    @Primary
    public DataSource tenantDataSource(@Qualifier("platformDataSource") DataSource platformDataSource) {
        log.info("Configuring tenant data source router");
        return new TenantDataSourceRouter(tenantRegistry, platformDataSource);
    }

    /**
     * EntityManagerFactory for tenant-specific entities.
     * Scans packages: authorization, invitation (roles, permissions, user_roles,
     * invitations).
     */
    @Bean(name = "tenantEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("tenantDataSource") DataSource tenantDataSource) {

        log.info("Configuring tenant EntityManagerFactory");

        return builder
                .dataSource(tenantDataSource)
                .packages(
                        "com.learning.authservice.authorization.domain",
                        "com.learning.authservice.invitation.domain")
                .persistenceUnit("tenant")
                .properties(java.util.Map.of(
                        "hibernate.hbm2ddl.auto", "none" // Disable schema validation - Flyway manages schemas
                ))
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

    /**
     * EntityManagerFactory for platform entities.
     * Currently not used since tenant table is in platform-service.
     * Kept for future platform-level auth-service tables.
     */
    @Bean(name = "platformEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("platformDataSource") DataSource platformDataSource) {

        log.info("Configuring platform EntityManagerFactory");

        return builder
                .dataSource(platformDataSource)
                .packages("com.learning.authservice.platform.domain") // Reserved for future platform entities
                .persistenceUnit("platform")
                .build();
    }

    /**
     * Transaction manager for platform entities.
     */
    @Bean(name = "platformTransactionManager")
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("platformEntityManagerFactory") EntityManagerFactory platformEntityManagerFactory) {

        return new JpaTransactionManager(platformEntityManagerFactory);
    }
}
