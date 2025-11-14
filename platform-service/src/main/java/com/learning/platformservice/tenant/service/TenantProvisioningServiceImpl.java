package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.action.TenantProvisionAction;
import com.learning.platformservice.tenant.action.TenantProvisionContext;
import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
public class TenantProvisioningServiceImpl implements TenantProvisioningService {
    private final TenantRepository tenantRepository;
    private final Counter attemptsCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final List<TenantProvisionAction> actions;

    @Autowired
    public TenantProvisioningServiceImpl(TenantRepository tenantRepository,
                                         MeterRegistry meterRegistry,
                                         List<TenantProvisionAction> actions) {
        this.tenantRepository = tenantRepository;
        this.attemptsCounter = Counter.builder("platform.tenants.provision.attempts").description("Tenant provision attempts").register(meterRegistry);
        this.successCounter = Counter.builder("platform.tenants.provision.success").description("Successful tenant provisions").register(meterRegistry);
        this.failureCounter = Counter.builder("platform.tenants.provision.failure").description("Failed tenant provisions").register(meterRegistry);
        this.actions = actions;
    }

    @Override
    @Transactional
    public TenantDto provision(ProvisionTenantRequest request) {
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
