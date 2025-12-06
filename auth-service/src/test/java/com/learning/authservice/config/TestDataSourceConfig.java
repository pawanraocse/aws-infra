package com.learning.authservice.config;

import com.learning.authservice.tenant.AuthServiceTenantRegistry;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Test-specific datasource configuration.
 * Overrides the production tenant datasource router to provide a default
 * datasource
 * for Hibernate initialization, while still maintaining routing capability
 * during tests.
 * 
 * This solves the chicken-and-egg problem where Hibernate needs a connection
 * during
 * EntityManagerFactory creation, but TenantContext isn't set until @BeforeEach.
 */
@TestConfiguration
public class TestDataSourceConfig {

    /**
     * Override the tenant datasource bean to provide a default datasource.
     * Uses the static Testcontainers PostgreSQL instance from
     * AbstractIntegrationTest.
     * The router can still dynamically route to different tenant DBs when context
     * is set.
     */
    @Bean(name = "tenantDataSource")
    @Primary
    public DataSource tenantDataSource(AuthServiceTenantRegistry tenantRegistry) {
        // Get reference to static Testcontainers instance
        var postgres = AbstractIntegrationTest.postgres;

        // Create a default datasource for test (points to the Testcontainers DB)
        HikariDataSource defaultDataSource = new HikariDataSource();
        defaultDataSource.setJdbcUrl(postgres.getJdbcUrl());
        defaultDataSource.setUsername(postgres.getUsername());
        defaultDataSource.setPassword(postgres.getPassword());
        defaultDataSource.setMaximumPoolSize(5);
        defaultDataSource.setPoolName("test-default-pool");

        // Return router with default datasource for Hibernate initialization
        return new TenantDataSourceRouter(tenantRegistry, defaultDataSource);
    }
}
