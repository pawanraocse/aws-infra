package com.learning.authservice.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

/**
 * Runs Flyway migrations on tenant databases.
 * Each tenant gets their own set of tables in their dedicated database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {

    /**
     * Run Flyway migrations for a tenant database.
     * 
     * @param jdbcUrl  Tenant database JDBC URL
     * @param username Database username
     * @param password Database password
     * @return Last migration version applied
     */
    public String migrateTenant(String jdbcUrl, String username, String password) {
        long start = System.currentTimeMillis();
        log.info("tenant_migration_start jdbcUrl={}", jdbcUrl);

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(true)
                .table("flyway_auth_history")
                .validateOnMigrate(true)
                .load();

        var result = flyway.migrate();
        String lastVersion = result.migrations.isEmpty()
                ? "baseline"
                : result.migrations.get(result.migrations.size() - 1).version.toString();

        log.info("tenant_migration_success jdbcUrl={} lastVersion={} durationMs={}",
                jdbcUrl, lastVersion, System.currentTimeMillis() - start);
        return lastVersion;
    }
}
