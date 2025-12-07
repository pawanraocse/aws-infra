package com.learning.backendservice.config;

import com.learning.backendservice.tenant.registry.TenantRegistryService;
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
 * Configures tenant-aware data sources for multi-tenant architecture.
 * Uses TenantDataSourceRouter to dynamically route connections based on
 * X-Tenant-Id header.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.learning.backendservice.repository", entityManagerFactoryRef = "tenantEntityManagerFactory", transactionManagerRef = "tenantTransactionManager")
@RequiredArgsConstructor
@Slf4j
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username}")
    private String defaultUsername;

    @Value("${spring.datasource.password}")
    private String defaultPassword;

    private final TenantRegistryService tenantRegistry;

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
        dataSource.setPoolName("default-pool");

        return dataSource;
    }

    /**
     * Tenant DataSource Router - dynamically routes to tenant databases.
     */
    @Bean(name = "tenantDataSource")
    @Primary
    public DataSource tenantDataSource(@Qualifier("defaultDataSource") DataSource defaultDataSource) {
        log.info("Configuring tenant data source router");
        return new TenantDataSourceRouter(tenantRegistry, defaultDataSource);
    }

    /**
     * EntityManagerFactory for tenant-specific entities.
     */
    @Bean(name = "tenantEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("tenantDataSource") DataSource tenantDataSource) {

        log.info("Configuring tenant EntityManagerFactory");

        return builder
                .dataSource(tenantDataSource)
                .packages("com.learning.backendservice.entity")
                .persistenceUnit("tenant")
                .properties(java.util.Map.of(
                        "hibernate.hbm2ddl.auto", "none"))
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
