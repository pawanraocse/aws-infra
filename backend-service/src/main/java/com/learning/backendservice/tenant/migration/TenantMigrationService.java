package com.learning.backendservice.tenant.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {

    public String migrateTenant(String jdbcUrl, String username, String password) {
        long start = System.currentTimeMillis();
        log.info("tenant_migration_start jdbcUrl={}", jdbcUrl);
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .table("flyway_backend_history")
                .validateOnMigrate(true)
                .load();
        var result = flyway.migrate();
        String lastVersion = result.migrations.isEmpty() ? "baseline" : result.migrations.get(result.migrations.size() - 1).version.toString();
        log.info("tenant_migration_success jdbcUrl={} lastVersion={} durationMs={}", jdbcUrl, lastVersion, System.currentTimeMillis() - start);
        return lastVersion;
    }
}

