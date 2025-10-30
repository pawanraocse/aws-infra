package com.learning.backendservice.service;

import com.learning.backendservice.dto.TenantRequestDto;
import com.learning.backendservice.dto.TenantResponseDto;
import com.learning.backendservice.entity.Tenant;
import com.learning.backendservice.exception.ResourceNotFoundException;
import com.learning.backendservice.exception.TenantProvisioningException;
import com.learning.backendservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;

    @Override
    @Transactional
    public TenantResponseDto createTenant(TenantRequestDto request) {
        log.info("Creating tenant: {}", request.getTenantId());

        if (tenantRepository.existsByTenantId(request.getTenantId())) {
            throw new IllegalArgumentException("Tenant already exists: " + request.getTenantId());
        }

        String schemaName = "tenant_" + request.getTenantId();

        Tenant tenant = Tenant.builder()
                .tenantId(request.getTenantId())
                .tenantName(request.getTenantName())
                .schemaName(schemaName)
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        Tenant saved = tenantRepository.save(tenant);

        try {
            provisionTenantSchema(request.getTenantId());
        } catch (Exception e) {
            log.error("Failed to provision schema, rolling back tenant creation", e);
            throw new TenantProvisioningException("Failed to provision tenant schema", e);
        }

        log.info("Tenant created successfully: {}", request.getTenantId());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantResponseDto getTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
        return toDto(tenant);
    }

    @Override
    public void provisionTenantSchema(String tenantId) {
        String schemaName = "tenant_" + tenantId;

        log.info("Provisioning schema for tenant: {} -> {}", tenantId, schemaName);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schemaName)
                    .locations("classpath:db/tenant-template")
                    .baselineOnMigrate(true)
                    .load();

            flyway.migrate();

            log.info("Schema provisioned successfully: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to provision schema for tenant: {}", tenantId, e);
            throw new TenantProvisioningException("Failed to provision tenant schema", e);
        }
    }

    private TenantResponseDto toDto(Tenant tenant) {
        return TenantResponseDto.builder()
                .id(tenant.getId())
                .tenantId(tenant.getTenantId())
                .tenantName(tenant.getTenantName())
                .schemaName(tenant.getSchemaName())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .createdBy(tenant.getCreatedBy())
                .build();
    }
}
