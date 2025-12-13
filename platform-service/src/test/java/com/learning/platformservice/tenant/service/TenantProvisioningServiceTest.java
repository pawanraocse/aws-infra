package com.learning.platformservice.tenant.service;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.platformservice.membership.service.MembershipService;
import com.learning.platformservice.tenant.action.TenantProvisionAction;
import com.learning.platformservice.tenant.config.PlatformTenantProperties;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantProvisioningServiceTest {

        @Mock
        TenantRepository tenantRepository;
        @Mock
        TenantProvisionAction storageAction;
        @Mock
        TenantProvisionAction migrationAction;
        @Mock
        TenantProvisionAction auditAction;
        @Mock
        TenantProvisioner tenantProvisioner;
        @Mock
        MembershipService membershipService;

        PlatformTenantProperties tenantProperties;
        SimpleMeterRegistry meterRegistry;
        TenantProvisioningService service;

        @BeforeEach
        void init() {
                MockitoAnnotations.openMocks(this);
                meterRegistry = new SimpleMeterRegistry();
                tenantProperties = new PlatformTenantProperties();
                service = new TenantProvisioningServiceImpl(
                                tenantRepository,
                                meterRegistry,
                                List.of(storageAction, migrationAction, auditAction),
                                tenantProperties,
                                tenantProvisioner,
                                membershipService);
        }

        @Test
        @DisplayName("Should provision tenant successfully and invoke actions in order")
        void provisionTenant_success() {
                ProvisionTenantRequest req = ProvisionTenantRequest.forOrganization("acme", "Acme Corp",
                                "admin@acme.com",
                                "STANDARD");
                when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
                when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

                TenantDto dto = service.provision(req);

                assertThat(dto).isNotNull();
                assertThat(dto.id()).isEqualTo("acme");
                assertThat(dto.status()).isEqualTo("ACTIVE");

                InOrder inOrder = inOrder(storageAction, migrationAction, auditAction);
                inOrder.verify(storageAction).execute(any());
                inOrder.verify(migrationAction).execute(any());
                inOrder.verify(auditAction).execute(any());

                verify(tenantRepository, atLeast(2)).save(any(Tenant.class));
        }

        @Test
        @DisplayName("Should throw conflict when tenant already exists")
        void provisionTenant_conflict() {
                ProvisionTenantRequest req = ProvisionTenantRequest.forOrganization("acme", "Acme Corp",
                                "admin@acme.com",
                                "STANDARD");
                when(tenantRepository.findById("acme")).thenReturn(Optional.of(new Tenant()));

                assertThatThrownBy(() -> service.provision(req))
                                .isInstanceOf(TenantAlreadyExistsException.class);

                verifyNoInteractions(storageAction, migrationAction, auditAction);
        }

        @Test
        @DisplayName("Should mark tenant PROVISION_ERROR when an action fails")
        void provisionTenant_actionFailure() {
                ProvisionTenantRequest req = ProvisionTenantRequest.forOrganization("failco", "Fail Co",
                                "admin@failco.com",
                                "STANDARD");
                when(tenantRepository.findById("failco")).thenReturn(Optional.empty());
                when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
                doThrow(new RuntimeException("boom")).when(migrationAction).execute(any());

                assertThatThrownBy(() -> service.provision(req))
                                .isInstanceOf(TenantProvisioningException.class)
                                .hasMessageContaining("Failed provisioning");

                ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
                verify(tenantRepository, atLeast(2)).save(captor.capture());
                boolean errorStatusSeen = captor.getAllValues().stream()
                                .anyMatch(t -> "PROVISION_ERROR".equals(t.getStatus()));
                assertThat(errorStatusSeen).isTrue();

                verify(auditAction, never()).execute(any());
        }

        @Test
        @DisplayName("Drops tenant database on failure when dropOnFailure true and storageMode=DATABASE")
        void provisionTenant_dropOnFailure() {
                tenantProperties.setDropOnFailure(true);
                tenantProperties.setTenantDatabaseModeEnabled(true);
                tenantProperties.setDbPerTenantEnabled(true);
                ProvisionTenantRequest req = ProvisionTenantRequest.forOrganization("dbfail", "DB Fail Co",
                                "admin@dbfail.com",
                                "STANDARD");
                when(tenantRepository.findById("dbfail")).thenReturn(Optional.empty());
                when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
                doThrow(new RuntimeException("boom")).when(migrationAction).execute(any());

                assertThatThrownBy(() -> service.provision(req))
                                .isInstanceOf(TenantProvisioningException.class);

                verify(tenantProvisioner, times(1)).dropTenantDatabase("dbfail");
        }
}
