package com.learning.backendservice.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway configuration for backend-service.
 * Runs migrations on personal_shared database at startup.
 * Same migrations are also run on org tenant DBs via TenantMigrationService.
 */
@Configuration
@Slf4j
public class FlywayConfig {

    /**
     * Flyway for personal_shared database.
     * Runs db/migration scripts on startup.
     * Uses separate history table to avoid conflicts with other services.
     */
    @Bean(name = "personalSharedFlyway", initMethod = "migrate")
    public Flyway personalSharedFlyway(@Qualifier("personalSharedDataSource") DataSource personalSharedDataSource) {
        log.info("Configuring Flyway for personal_shared database (backend-service)");

        Flyway flyway = Flyway.configure()
                .dataSource(personalSharedDataSource)
                .locations("classpath:db/migration")
                .table("flyway_backend_history")  // Unique per service
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load();

        log.info("Backend Flyway configured - will run migrations on personal_shared");
        return flyway;
    }
}
