package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.entity.TenantMigrationHistory;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.repo.TenantMigrationHistoryRepository;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class TenantProvisioningServiceImpl implements TenantProvisioningService {
    private final TenantRepository tenantRepository;
    private final TenantMigrationHistoryRepository migrationHistoryRepository;
    private final TenantProvisioner tenantProvisioner;
    private final Counter attemptsCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer migrationTimer;

    public TenantProvisioningServiceImpl(TenantRepository tenantRepository,
                                         TenantMigrationHistoryRepository migrationHistoryRepository,
                                         TenantProvisioner tenantProvisioner,
                                         MeterRegistry meterRegistry) {
        this.tenantRepository = tenantRepository;
        this.migrationHistoryRepository = migrationHistoryRepository;
        this.tenantProvisioner = tenantProvisioner;
        this.attemptsCounter = Counter.builder("platform.tenants.provision.attempts").description("Tenant provision attempts").register(meterRegistry);
        this.successCounter = Counter.builder("platform.tenants.provision.success").description("Successful tenant provisions").register(meterRegistry);
        this.failureCounter = Counter.builder("platform.tenants.provision.failure").description("Failed tenant provisions").register(meterRegistry);
        this.migrationTimer = Timer.builder("platform.tenants.migration.duration").description("Time for tenant migration script execution").register(meterRegistry);
    }

    @Override
    @Transactional
    public TenantDto provision(ProvisionTenantRequest request) {
        attemptsCounter.increment();
        String tenantId = request.id();
        if (tenantRepository.findById(tenantId).isPresent()) {
            throw new TenantAlreadyExistsException(tenantId);
        }
        // Insert PROVISIONING row
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(request.name());
        tenant.setStatus("PROVISIONING");
        tenant.setStorageMode(request.storageMode());
        tenant.setSlaTier(request.slaTier());
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        long start = System.currentTimeMillis();
        String jdbcUrl;
        String lastVersion = "V1__init_platform_service"; // baseline placeholder
        TenantMigrationHistory mh = new TenantMigrationHistory();
        mh.setTenantId(tenantId);
        mh.setVersion(lastVersion);
        mh.setStatus("STARTED");
        migrationHistoryRepository.save(mh);
        try {
            jdbcUrl = tenantProvisioner.provisionTenantStorage(tenantId, request.storageMode());
            // Placeholder for migration timing
            migrationTimer.record(() -> {
                // TODO: per-tenant Flyway migrations execution
            });
            mh.setStatus("SUCCESS");
            mh.setEndedAt(OffsetDateTime.now());
            migrationHistoryRepository.save(mh);
        } catch (Exception e) {
            failureCounter.increment();
            mh.setStatus("FAILED");
            mh.setEndedAt(OffsetDateTime.now());
            mh.setNotes(e.getMessage());
            migrationHistoryRepository.save(mh);
            tenant.setStatus("PROVISION_ERROR");
            tenant.setUpdatedAt(OffsetDateTime.now());
            tenantRepository.save(tenant);
            log.error("tenant_provision_failed tenantId={} error={}", tenantId, e.getMessage(), e);
            throw new TenantProvisioningException(tenantId, "Failed provisioning: " + e.getMessage(), e);
        }
        // Finalize row
        tenant.setJdbcUrl(jdbcUrl);
        tenant.setLastMigrationVersion(lastVersion);
        tenant.setStatus("ACTIVE");
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);
        successCounter.increment();
        log.info("tenant_provisioned tenantId={} storageMode={} durationMs={}", tenantId, request.storageMode(), System.currentTimeMillis() - start);
        return new TenantDto(tenant.getId(), tenant.getName(), tenant.getStatus(), tenant.getStorageMode(), tenant.getSlaTier(), tenant.getJdbcUrl());
    }
}
