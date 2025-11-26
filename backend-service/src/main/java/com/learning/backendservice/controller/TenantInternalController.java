package com.learning.backendservice.controller;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/tenants")
@Slf4j
public class TenantInternalController {

    @PostMapping("/{tenantId}/migrate")
    public ResponseEntity<MigrationResult> migrateTenant(
            @PathVariable String tenantId,
            @RequestBody @Valid TenantDbConfig dbConfig) {
        log.info("Starting tenant migration: tenantId={}", tenantId);

        try (HikariDataSource tenantDataSource = createTenantDataSource(dbConfig)) {

            // Configure Flyway for tenant database
            Flyway flyway = Flyway.configure()
                    .dataSource(tenantDataSource)
                    .locations("classpath:db/tenant-template")
                    .table(tenantId + "_schema_history") // Tenant-specific history table
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load();

            // Execute migrations
            MigrateResult result = flyway.migrate();

            String version = result.targetSchemaVersion != null
                    ? result.targetSchemaVersion
                    : "baseline";

            log.info("✅ Tenant migration completed: tenantId={} migrations={} version={}",
                    tenantId, result.migrationsExecuted, version);

            return ResponseEntity.ok(new MigrationResult(
                    true,
                    result.migrationsExecuted,
                    version));

        } catch (Exception e) {
            log.error("❌ Tenant migration failed: tenantId={} error={}",
                    tenantId, e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MigrationResult(false, 0, null));
        }
    }

    private HikariDataSource createTenantDataSource(TenantDbConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(5); // Small pool for migrations
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setPoolName("migration-" + UUID.randomUUID());

        return new HikariDataSource(hikariConfig);
    }

    // DTOs
    public record TenantDbConfig(
            @NotBlank String jdbcUrl,
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record MigrationResult(
            boolean success,
            int migrationsExecuted,
            String lastVersion) {
    }
}
