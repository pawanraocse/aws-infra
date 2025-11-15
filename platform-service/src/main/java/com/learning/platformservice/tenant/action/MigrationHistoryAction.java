package com.learning.platformservice.tenant.action;

import com.learning.platformservice.tenant.entity.TenantMigrationHistory;
import com.learning.platformservice.tenant.provision.TenantMigrationRunner;
import com.learning.platformservice.tenant.repo.TenantMigrationHistoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Records baseline migration history and measures migration execution timing (placeholder for per-tenant flyway).
 */
@Component
@Slf4j
@Order(20)
public class MigrationHistoryAction implements TenantProvisionAction {

    private final TenantMigrationHistoryRepository migrationHistoryRepository;
    private final Timer migrationTimer;
    private final TenantMigrationRunner migrationRunner;
    private final String masterUsername;
    private final String masterPassword;

    public MigrationHistoryAction(TenantMigrationHistoryRepository migrationHistoryRepository,
                                  MeterRegistry meterRegistry,
                                  TenantMigrationRunner migrationRunner,
                                  @Value("${spring.datasource.username:postgres}") String masterUsername,
                                  @Value("${spring.datasource.password:postgres}") String masterPassword) {
        this.migrationHistoryRepository = migrationHistoryRepository;
        this.migrationRunner = migrationRunner;
        this.masterUsername = masterUsername;
        this.masterPassword = masterPassword;
        this.migrationTimer = Timer.builder("platform.tenants.migration.duration")
                .description("Time for tenant migration script execution")
                .register(meterRegistry);
    }

    @Override
    public void execute(TenantProvisionContext context) {
        String tenantId = context.getTenant().getId();
        String jdbcUrl = context.getJdbcUrl();
        String storageMode = context.getRequest().storageMode();
        TenantMigrationHistory history = new TenantMigrationHistory();
        history.setTenantId(tenantId);
        history.setStatus("STARTED");
        // Assign provisional version early (baseline) to satisfy NOT NULL constraint
        history.setVersion("baseline");
        migrationHistoryRepository.save(history);
        try {
            String lastVersion;
            if ("SCHEMA".equalsIgnoreCase(storageMode)) {
                lastVersion = "baseline";
            } else {
                lastVersion = migrationTimer.record(() -> {
                    if (jdbcUrl == null || jdbcUrl.isBlank()) return "baseline";
                    return migrationRunner.runMigrations(jdbcUrl, masterUsername, masterPassword);
                });
            }
            history.setVersion(lastVersion);
            history.setStatus("SUCCESS");
            history.setEndedAt(OffsetDateTime.now());
            migrationHistoryRepository.save(history);
            context.setLastMigrationVersion(lastVersion);
        } catch (Exception e) {
            history.setStatus("FAILED");
            history.setEndedAt(OffsetDateTime.now());
            history.setNotes(e.getMessage());
            migrationHistoryRepository.save(history);
            log.error("migration_failed tenantId={} error={}", tenantId, e.getMessage(), e);
            throw e;
        }
    }
}
