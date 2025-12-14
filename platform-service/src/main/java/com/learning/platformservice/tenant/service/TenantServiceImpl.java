package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TenantServiceImpl implements TenantService {
    private final TenantRepository tenantRepository;

    public TenantServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantDto> getTenant(String id) {
        var dto = tenantRepository.findById(id)
                .map(this::toDto);
        log.debug("tenant_lookup tenantId={} found={}", id, dto.isPresent());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantDto> getAllTenants() {
        log.debug("Fetching all tenants for platform management");
        return tenantRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    private TenantDto toDto(com.learning.platformservice.tenant.entity.Tenant t) {
        return new TenantDto(
                t.getId(),
                t.getName(),
                t.getTenantType() != null ? t.getTenantType().name() : "PERSONAL",
                t.getStatus(),
                t.getStorageMode(),
                t.getSlaTier(),
                t.getJdbcUrl(),
                t.getLastMigrationVersion());
    }
}
