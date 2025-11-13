package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class TenantServiceImpl implements TenantService {
    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);

    private final TenantRepository tenantRepository;

    public TenantServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public TenantDto provisionTenant(ProvisionTenantRequest request) {
        var now = OffsetDateTime.now();
        Tenant tenant = new Tenant();
        tenant.setId(request.id());
        tenant.setName(request.name());
        tenant.setStatus("ACTIVE");
        tenant.setStorageMode(request.storageMode());
        tenant.setSlaTier(request.slaTier());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantRepository.save(tenant);

        log.info("operation=provision_tenant tenantId={} storageMode={} slaTier={}", request.id(), request.storageMode(), request.slaTier());

        return new TenantDto(
                tenant.getId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getStorageMode(),
                tenant.getSlaTier(),
                tenant.getJdbcUrl()
        );
    }

    @Override
    public Optional<TenantDto> getTenant(String id) {
        return tenantRepository.findById(id)
                .map(t -> new TenantDto(t.getId(), t.getName(), t.getStatus(), t.getStorageMode(), t.getSlaTier(), t.getJdbcUrl()));
    }
}
