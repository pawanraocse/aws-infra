package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.action.TenantProvisionAction;
import com.learning.platformservice.tenant.action.TenantProvisionContext;
import com.learning.platformservice.tenant.config.PlatformTenantProperties;
import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Primary
@Slf4j
public class TenantProvisioningServiceImpl implements TenantProvisioningService {
    private final TenantRepository tenantRepository;
    private final Counter attemptsCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final List<TenantProvisionAction> actions;
    private final PlatformTenantProperties tenantProperties;
    private final TenantProvisioner tenantProvisioner;

    @Autowired
    public TenantProvisioningServiceImpl(TenantRepository tenantRepository,
                                          MeterRegistry meterRegistry,
                                          List<TenantProvisionAction> actions,
                                          PlatformTenantProperties tenantProperties,
                                          TenantProvisioner tenantProvisioner) {
        this.tenantRepository = tenantRepository;
        this.attemptsCounter = Counter.builder("platform.tenants.provision.attempts").description("Tenant provision attempts").register(meterRegistry);
        this.successCounter = Counter.builder("platform.tenants.provision.success").description("Successful tenant provisions").register(meterRegistry);
        this.failureCounter = Counter.builder("platform.tenants.provision.failure").description("Failed tenant provisions").register(meterRegistry);
        this.actions = actions;
        this.tenantProperties = tenantProperties;
        this.tenantProvisioner = tenantProvisioner;
    }

    @Override
    public TenantDto provision(ProvisionTenantRequest request) {
        log.debug("provision_flags dbPerTenantEnabled={} databaseModeEnabled={}", tenantProperties.isDbPerTenantEnabled(), tenantProperties.isTenantDatabaseModeEnabled());
        attemptsCounter.increment();
        String tenantId = request.id();
        if (tenantRepository.findById(tenantId).isPresent()) {
            throw new TenantAlreadyExistsException(tenantId);
        }
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
        TenantProvisionContext ctx = new TenantProvisionContext(request, tenant);
        try {
            for (TenantProvisionAction action : actions) {
                action.execute(ctx);
            }
        } catch (Exception e) {
            failureCounter.increment();
            tenant.setStatus("PROVISION_ERROR");
            tenant.setUpdatedAt(OffsetDateTime.now());
            tenantRepository.save(tenant);
            if ("DATABASE".equalsIgnoreCase(request.storageMode()) && tenantProperties.isDropOnFailure()) {
                try { tenantProvisioner.dropTenantDatabase(tenantId); } catch (Exception dropEx) { log.warn("tenant_db_drop_failed tenantId={} error={}", tenantId, dropEx.getMessage(), dropEx);} }
            log.error("tenant_provision_failed tenantId={} error={}", tenantId, e.getMessage(), e);
            throw new TenantProvisioningException(tenantId, "Failed provisioning: " + e.getMessage(), e);
        }
        tenant.setJdbcUrl(ctx.getJdbcUrl());
        tenant.setLastMigrationVersion(ctx.getLastMigrationVersion());
        tenant.setStatus("ACTIVE");
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);
        successCounter.increment();
        log.info("tenant_provisioned tenantId={} storageMode={} durationMs={}", tenantId, request.storageMode(), System.currentTimeMillis() - start);
        return new TenantDto(tenant.getId(), tenant.getName(), tenant.getStatus(), tenant.getStorageMode(),
                tenant.getSlaTier(), tenant.getJdbcUrl(), tenant.getLastMigrationVersion());
    }
}
