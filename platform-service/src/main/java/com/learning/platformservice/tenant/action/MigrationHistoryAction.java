package com.learning.platformservice.tenant.action;

import com.learning.platformservice.tenant.entity.TenantMigrationHistory;
import com.learning.platformservice.tenant.repo.TenantMigrationHistoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
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

    public MigrationHistoryAction(TenantMigrationHistoryRepository migrationHistoryRepository,
                                  MeterRegistry meterRegistry) {
        this.migrationHistoryRepository = migrationHistoryRepository;
        this.migrationTimer = Timer.builder("platform.tenants.migration.duration")
                .description("Time for tenant migration script execution")
                .register(meterRegistry);
    }

    @Override
    public void execute(TenantProvisionContext context) {
        String tenantId = context.getTenant().getId();
        String version = "V1__init_platform_service"; // baseline placeholder
        TenantMigrationHistory history = new TenantMigrationHistory();
        history.setTenantId(tenantId);
        history.setVersion(version);
        history.setStatus("STARTED");
        migrationHistoryRepository.save(history);
        try {
            migrationTimer.record(() -> {
                // TODO: run per-tenant migrations
            });
            history.setStatus("SUCCESS");
            history.setEndedAt(OffsetDateTime.now());
            migrationHistoryRepository.save(history);
            context.setLastMigrationVersion(version);
        } catch (Exception e) {
            history.setStatus("FAILED");
            history.setEndedAt(OffsetDateTime.now());
            history.setNotes(e.getMessage());
            migrationHistoryRepository.save(history);
            log.error("migration_failed tenantId={} error={}", tenantId, e.getMessage(), e);
            throw e; // will be wrapped by service layer
        }
    }
}
