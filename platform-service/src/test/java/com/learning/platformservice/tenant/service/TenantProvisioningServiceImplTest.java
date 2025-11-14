package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.action.TenantProvisionAction;
import com.learning.platformservice.tenant.action.TenantProvisionContext;
import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Focused tests for TenantProvisioningServiceImpl action pipeline & metrics.
 */
class TenantProvisioningServiceImplTest {

    @Mock
    TenantRepository tenantRepository;

    SimpleMeterRegistry meterRegistry;
    TenantProvisioningServiceImpl service;

    // Track execution order
    List<String> executed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
    }

    private void initServiceWithActions(List<TenantProvisionAction> actions) {
        service = new TenantProvisioningServiceImpl(tenantRepository, meterRegistry, actions);
    }

    // Stub actions ---------------------------------------------------------
    private TenantProvisionAction storageAction() {
        return ctx -> {
            executed.add("storage");
            ctx.setJdbcUrl("jdbc:test://tenant_" + ctx.getTenant().getId());
        }; }

    private TenantProvisionAction migrationAction() {
        return ctx -> {
            executed.add("migration");
            ctx.setLastMigrationVersion("V1__init");
        }; }

    private TenantProvisionAction auditAction() {
        return ctx -> executed.add("audit"); }

    private TenantProvisionAction failing(String name) {
        return ctx -> {
            executed.add(name);
            throw new RuntimeException("fail at " + name);
        }; }

    // Helper for repository stubbing
    private void stubTenantPersistence() {
        when(tenantRepository.findById(any())).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Pipeline executes actions in order and enriches context")
    void pipelineSuccess_enrichment() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), migrationAction(), auditAction()));

        ProvisionTenantRequest req = new ProvisionTenantRequest("acme", "Acme", "SCHEMA", "STANDARD");
        TenantDto dto = service.provision(req);

        assertThat(executed).containsExactly("storage", "migration", "audit");
        assertThat(dto.jdbcUrl()).contains("tenant_acme");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.lastMigrationVersion()).isEqualTo("V1__init");

        // Metrics
        assertThat(meterRegistry.find("platform.tenants.provision.attempts").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("platform.tenants.provision.success").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("platform.tenants.provision.failure").counter().count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Failure in middle action stops subsequent actions and marks PROVISION_ERROR")
    void pipelineFailure_mid() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), failing("migration"), auditAction()));

        ProvisionTenantRequest req = new ProvisionTenantRequest("failmid", "Fail Mid", "SCHEMA", "STANDARD");

        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantProvisioningException.class)
                .hasMessageContaining("Failed provisioning");

        assertThat(executed).containsExactly("storage", "migration"); // audit skipped

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, atLeast(2)).save(captor.capture());
        boolean sawError = captor.getAllValues().stream().anyMatch(t -> "PROVISION_ERROR".equals(t.getStatus()));
        assertThat(sawError).isTrue();

        // Metrics
        assertThat(meterRegistry.find("platform.tenants.provision.attempts").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("platform.tenants.provision.success").counter().count()).isEqualTo(0);
        assertThat(meterRegistry.find("platform.tenants.provision.failure").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Failure in first action only executes that action")
    void pipelineFailure_first() {
        stubTenantPersistence();
        initServiceWithActions(List.of(failing("storage"), migrationAction(), auditAction()));

        ProvisionTenantRequest req = new ProvisionTenantRequest("failfirst", "Fail First", "SCHEMA", "STANDARD");

        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(executed).containsExactly("storage");
        assertThat(executed).doesNotContain("migration", "audit");

        // Metrics
        assertThat(meterRegistry.find("platform.tenants.provision.failure").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Failure in last action executes previous actions and marks error")
    void pipelineFailure_last() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), migrationAction(), failing("audit")));

        ProvisionTenantRequest req = new ProvisionTenantRequest("faillast", "Fail Last", "SCHEMA", "STANDARD");

        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(executed).containsExactly("storage", "migration", "audit");

        // Metrics
        assertThat(meterRegistry.find("platform.tenants.provision.failure").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("platform.tenants.provision.success").counter().count()).isEqualTo(0);
    }
}

