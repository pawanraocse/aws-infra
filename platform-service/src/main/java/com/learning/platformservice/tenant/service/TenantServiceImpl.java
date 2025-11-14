package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .map(t -> new TenantDto(t.getId(), t.getName(), t.getStatus(), t.getStorageMode(), t.getSlaTier(), t.getJdbcUrl()));
        log.debug("tenant_lookup tenantId={} found={}", id, dto.isPresent());
        return dto;
    }
}
