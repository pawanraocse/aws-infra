package com.learning.authservice.controller;

import com.learning.common.dto.MigrationResult;
import com.learning.common.dto.TenantDbConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal controller for tenant migrations.
 * Called by platform-service during tenant provisioning.
 * Uses tenant user credentials so tables are owned by tenant user.
 */
@RestController
@RequestMapping("/internal/tenants")
@Slf4j
public class TenantInternalController {

        private final com.learning.common.infra.openfga.OpenFgaStoreService openFgaStoreService;
        private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        public TenantInternalController(
                        com.learning.common.infra.openfga.OpenFgaStoreService openFgaStoreService,
                        @org.springframework.beans.factory.annotation.Qualifier("tenantDataSource") javax.sql.DataSource tenantDataSource) {
                this.openFgaStoreService = openFgaStoreService;
                this.jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(tenantDataSource);
        }

        @PostMapping("/{tenantId}/migrate")
        public ResponseEntity<MigrationResult> migrateTenant(
                        @PathVariable String tenantId,
                        @RequestBody @Valid TenantDbConfig dbConfig) {
                log.info("Starting auth-service tenant migration: tenantId={}", tenantId);

                try (HikariDataSource tenantDataSource = createTenantDataSource(dbConfig)) {

                        // Configure Flyway for tenant database using tenant credentials
                        // This ensures tables are owned by tenant user
                        Flyway flyway = Flyway.configure()
                                        .dataSource(tenantDataSource)
                                        .locations("classpath:db/migration")
                                        .table("flyway_auth_history") // Auth-service specific history table
                                        .baselineOnMigrate(true)
                                        .baselineVersion("0")
                                        .validateOnMigrate(true)
                                        .load();

                        // Execute migrations
                        MigrateResult result = flyway.migrate();

                        String version = result.targetSchemaVersion != null
                                        ? result.targetSchemaVersion
                                        : "baseline";

                        log.info("✅ Auth-service tenant migration completed: tenantId={} migrations={} version={}",
                                        tenantId, result.migrationsExecuted, version);

                        // Create OpenFGA store for the tenant
                        String fgaStoreId = null;
                        try {
                                // Check if OpenFGA store needs to be created
                                log.info("Creating OpenFGA store for tenant: {}", tenantId);
                                fgaStoreId = openFgaStoreService.createStoreForTenant(tenantId, tenantId); // Use
                                                                                                           // tenantId
                                                                                                           // as name
                                log.info("✅ OpenFGA store created for tenant {}: {}", tenantId, fgaStoreId);
                        } catch (Exception e) {
                                // Log but don't fail the migration - OpenFGA might be optional/offline
                                log.error("⚠️ Failed to create OpenFGA store for tenant {}: {}", tenantId,
                                                e.getMessage());
                        }

                        return ResponseEntity.ok(new MigrationResult(
                                        true,
                                        result.migrationsExecuted,
                                        version,
                                        fgaStoreId));

                } catch (Exception e) {
                        log.error("❌ Auth-service tenant migration failed: tenantId={} error={}",
                                        tenantId, e.getMessage(), e);

                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(MigrationResult.failure());
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
                hikariConfig.setPoolName("auth-migration-" + UUID.randomUUID());

                return new HikariDataSource(hikariConfig);
        }

        /**
         * Seeds default roles and permissions for a personal tenant.
         * Called for SHARED mode tenants where we don't run full migrations
         * but need to insert tenant-specific seed data.
         */
        @PostMapping("/{tenantId}/seed-roles")
        public ResponseEntity<MigrationResult> seedRolesForTenant(@PathVariable String tenantId) {
                log.info("Seeding roles for personal tenant: tenantId={}", tenantId);

                try {
                        // Set tenant context so repository operations use the correct tenant_id
                        com.learning.common.infra.tenant.TenantContext.setCurrentTenant(tenantId);
                        try {
                                seedDefaultRolesAndPermissions(tenantId);
                                log.info("✅ Roles seeded successfully for personal tenant: {}", tenantId);
                                
                                // Create OpenFGA store for the personal tenant
                                String fgaStoreId = null;
                                try {
                                        log.info("Creating OpenFGA store for personal tenant: {}", tenantId);
                                        fgaStoreId = openFgaStoreService.createStoreForTenant(tenantId, tenantId);
                                        log.info("✅ OpenFGA store created for personal tenant {}: {}", tenantId, fgaStoreId);
                                } catch (Exception e) {
                                        log.error("⚠️ Failed to create OpenFGA store for personal tenant {}: {}", tenantId, e.getMessage());
                                }

                                return ResponseEntity.ok(new MigrationResult(true, 0, "seeded", fgaStoreId));
                        } finally {
                                com.learning.common.infra.tenant.TenantContext.clear();
                        }
                } catch (Exception e) {
                        log.error("❌ Failed to seed roles for personal tenant {}: {}", tenantId, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(MigrationResult.failure());
                }
        }

        /**
         * Seeds default roles and permissions for a tenant.
         * Similar to what org migrations do, but for personal tenants.
         */
        private void seedDefaultRolesAndPermissions(String tenantId) {
                // Use JDBC to insert seed data directly
                // TenantContext is already set, so routing goes to personal_shared DB
                
                // Check if roles already exist for this tenant
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE tenant_id = ?", Integer.class, tenantId);
                
                if (count != null && count > 0) {
                        log.info("Roles already exist for tenant {}, skipping seed", tenantId);
                        return;
                }
                
                // Insert default roles
                jdbcTemplate.update("""
                        INSERT INTO roles (id, tenant_id, name, description, scope, access_level, created_at, updated_at)
                        VALUES 
                        ('admin', ?, 'admin', 'Full administrative access', 'TENANT', 'ADMIN', NOW(), NOW()),
                        ('editor', ?, 'editor', 'Can edit content', 'TENANT', 'WRITE', NOW(), NOW()),
                        ('viewer', ?, 'viewer', 'Read-only access', 'TENANT', 'READ', NOW(), NOW())
                        ON CONFLICT (tenant_id, name) DO NOTHING
                        """, tenantId, tenantId, tenantId);
                
                // Insert default permissions
                jdbcTemplate.update("""
                        INSERT INTO permissions (id, tenant_id, resource, action, description, created_at)
                        VALUES 
                        ('entries:read', ?, 'entries', 'read', 'Read entries', NOW()),
                        ('entries:write', ?, 'entries', 'write', 'Write entries', NOW()),
                        ('entries:delete', ?, 'entries', 'delete', 'Delete entries', NOW()),
                        ('users:read', ?, 'users', 'read', 'View users', NOW()),
                        ('users:manage', ?, 'users', 'manage', 'Manage users', NOW()),
                        ('roles:manage', ?, 'roles', 'manage', 'Manage roles', NOW())
                        ON CONFLICT (tenant_id, resource, action) DO NOTHING
                        """, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId);
                
                // Insert role-permission mappings
                jdbcTemplate.update("""
                        INSERT INTO role_permissions (tenant_id, role_id, permission_id, created_at)
                        VALUES 
                        (?, 'admin', 'entries:read', NOW()),
                        (?, 'admin', 'entries:write', NOW()),
                        (?, 'admin', 'entries:delete', NOW()),
                        (?, 'admin', 'users:read', NOW()),
                        (?, 'admin', 'users:manage', NOW()),
                        (?, 'admin', 'roles:manage', NOW()),
                        (?, 'editor', 'entries:read', NOW()),
                        (?, 'editor', 'entries:write', NOW()),
                        (?, 'viewer', 'entries:read', NOW())
                        ON CONFLICT (tenant_id, role_id, permission_id) DO NOTHING
                        """, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId);
                
                log.info("Seeded {} default roles and permissions for tenant {}", 3, tenantId);
        }
}
