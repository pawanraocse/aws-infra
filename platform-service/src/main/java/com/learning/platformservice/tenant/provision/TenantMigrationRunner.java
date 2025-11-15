package com.learning.platformservice.tenant.provision;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.stereotype.Component;

/**
 * Runs Flyway migrations against a freshly created tenant database.
 */
@Component
@Slf4j
public class TenantMigrationRunner {

    /**
     * Execute baseline migrations for tenant DB and return last applied version.
     */
    public String runMigrations(String jdbcUrl, String username, String password) {
        log.debug("tenant_migration_start jdbcUrl={}", jdbcUrl);
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration") // reuse same migration set; can split later
                .baselineOnMigrate(true)
                .table("flyway_tenant_history")
                .validateOnMigrate(true)
                .load();
        MigrateResult result = flyway.migrate();
        String lastVersion = result.migrations.isEmpty() ? "baseline" : result.migrations.get(result.migrations.size() - 1).version.toString();
        log.info("tenant_migration_success jdbcUrl={} lastVersion={}", jdbcUrl, lastVersion);
        return lastVersion;
    }
}
