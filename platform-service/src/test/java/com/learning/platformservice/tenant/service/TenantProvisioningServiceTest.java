package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.entity.TenantMigrationHistory;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.repo.TenantMigrationHistoryRepository;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantProvisioningServiceTest {

    @Mock
    TenantRepository tenantRepository;
    @Mock
    TenantMigrationHistoryRepository migrationHistoryRepository;
    @Mock
    TenantProvisioner tenantProvisioner;

    SimpleMeterRegistry meterRegistry;

    TenantProvisioningService service;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        service = new TenantProvisioningServiceImpl(tenantRepository, migrationHistoryRepository, tenantProvisioner, meterRegistry);
    }

    @Test
    @DisplayName("Should provision tenant successfully (SCHEMA mode)")
    void provisionTenant_success() {
        ProvisionTenantRequest req = new ProvisionTenantRequest("acme", "Acme Corp", "SCHEMA", "STANDARD");
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantProvisioner.provisionTenantStorage("acme", "SCHEMA")).thenReturn("jdbc:postgresql://localhost:5432/awsinfra?currentSchema=tenant_acme");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(migrationHistoryRepository.save(any(TenantMigrationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.provision(req);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo("acme");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.jdbcUrl()).contains("tenant_acme");

        verify(tenantProvisioner).provisionTenantStorage("acme", "SCHEMA");
        verify(tenantRepository, times(2)).save(any(Tenant.class)); // initial + finalize
        verify(migrationHistoryRepository, atLeastOnce()).save(any(TenantMigrationHistory.class));
    }

    @Test
    @DisplayName("Should throw conflict when tenant already exists")
    void provisionTenant_conflict() {
        ProvisionTenantRequest req = new ProvisionTenantRequest("acme", "Acme Corp", "SCHEMA", "STANDARD");
        when(tenantRepository.findById("acme")).thenReturn(Optional.of(new Tenant()));

        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantAlreadyExistsException.class);
    }
}

