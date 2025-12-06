package com.learning.authservice.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

/**
 * Custom Flyway configuration for database-per-tenant architecture.
 * Separates platform and tenant migrations.
 */
@Configuration
@Slf4j
public class FlywayConfig {

    /**
     * Platform Flyway configuration.
     * Runs migrations on the shared 'awsinfra' database for platform-level tables.
     * Currently auth-service has no platform tables (tenant table is in
     * platform-service).
     */
    @Bean(name = "platformFlyway", initMethod = "migrate")
    @ConditionalOnProperty(prefix = "app.flyway.platform", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Flyway platformFlyway(@Qualifier("platformDataSource") DataSource platformDataSource) {
        log.info("Configuring Platform Flyway for awsinfra database");

        Flyway flyway = Flyway.configure()
                .dataSource(platformDataSource)
                .locations("classpath:db/migration/platform")
                .table("flyway_auth_platform_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load();

        log.info("Platform Flyway configured successfully");
        return flyway;
    }

    /**
     * Note: Tenant Flyway is NOT auto-migrated on startup.
     * Tenant migrations are invoked per-tenant by TenantMigrationService
     * when a tenant is provisioned or updated.
     */
}
