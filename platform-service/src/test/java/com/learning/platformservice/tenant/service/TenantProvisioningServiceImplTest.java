package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.action.TenantProvisionAction;
import com.learning.platformservice.tenant.config.PlatformTenantProperties;
import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Focused tests for TenantProvisioningServiceImpl action pipeline & metrics.
 */
class TenantProvisioningServiceImplTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantProvisioner tenantProvisioner;

    @Mock
    private PlatformTenantProperties platformTenantProperties;

    private SimpleMeterRegistry meterRegistry;
    private TenantProvisioningServiceImpl service;

    private final List<String> executed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
    }

    private void initServiceWithActions(List<TenantProvisionAction> actions) {
        service = new TenantProvisioningServiceImpl(tenantRepository, meterRegistry, actions,
                platformTenantProperties, tenantProvisioner);
    }

    // Stub actions ---------------------------------------------------------
    private TenantProvisionAction storageAction() {
        return ctx -> {
            executed.add("storage");
            ctx.setJdbcUrl("jdbc:test://tenant_" + ctx.getTenant().getId());
        };
    }

    private TenantProvisionAction migrationAction() {
        return ctx -> {
            executed.add("migration");
            ctx.setLastMigrationVersion("V1__init");
        };
    }

    private TenantProvisionAction auditAction() {
        return ctx -> executed.add("audit");
    }

    private TenantProvisionAction failing(String name) {
        return ctx -> {
            executed.add(name);
            throw new RuntimeException("fail at " + name);
        };
    }

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

    @Test
    @DisplayName("Database mode success does not invoke dropTenantDatabase")
    void databaseMode_success_noDrop() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), migrationAction(), auditAction()));
        when(platformTenantProperties.isDbPerTenantEnabled()).thenReturn(true);
        when(platformTenantProperties.isTenantDatabaseModeEnabled()).thenReturn(true);
        when(platformTenantProperties.isDropOnFailure()).thenReturn(false);

        ProvisionTenantRequest req = new ProvisionTenantRequest("dbsuccess", "DB Success", "DATABASE", "STANDARD");
        TenantDto dto = service.provision(req);

        assertThat(dto.status()).isEqualTo("ACTIVE");
        verify(tenantProvisioner, never()).dropTenantDatabase(any());
    }

    @Test
    @DisplayName("Database mode failure triggers drop when dropOnFailure true")
    void databaseMode_failure_triggersDrop() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), failing("migration"), auditAction()));
        when(platformTenantProperties.isDbPerTenantEnabled()).thenReturn(true);
        when(platformTenantProperties.isTenantDatabaseModeEnabled()).thenReturn(true);
        when(platformTenantProperties.isDropOnFailure()).thenReturn(true);

        ProvisionTenantRequest req = new ProvisionTenantRequest("dbfail", "DB Fail", "DATABASE", "STANDARD");
        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantProvisioningException.class)
                .hasMessageContaining("Failed provisioning");

        verify(tenantProvisioner, times(1)).dropTenantDatabase("dbfail");
    }

    @Test
    @DisplayName("Database mode failure does not drop when dropOnFailure false")
    void databaseMode_failure_noDropFlagFalse() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), failing("migration"), auditAction()));
        when(platformTenantProperties.isDbPerTenantEnabled()).thenReturn(true);
        when(platformTenantProperties.isTenantDatabaseModeEnabled()).thenReturn(true);
        when(platformTenantProperties.isDropOnFailure()).thenReturn(false);

        ProvisionTenantRequest req = new ProvisionTenantRequest("dbfailnodelete", "DB Fail No Delete", "DATABASE", "STANDARD");
        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantProvisioningException.class);

        verify(tenantProvisioner, never()).dropTenantDatabase(any());
    }

    @Test
    @DisplayName("Database mode disabled still attempts drop (document current behavior)")
    void databaseMode_disabledStillDropsDueToSimplifiedCheck() {
        stubTenantPersistence();
        initServiceWithActions(List.of(storageAction(), failing("migration"), auditAction()));
        when(platformTenantProperties.isDbPerTenantEnabled()).thenReturn(false);
        when(platformTenantProperties.isTenantDatabaseModeEnabled()).thenReturn(false);
        when(platformTenantProperties.isDropOnFailure()).thenReturn(true);

        ProvisionTenantRequest req = new ProvisionTenantRequest("dbdisabled", "DB Disabled", "DATABASE", "STANDARD");
        assertThatThrownBy(() -> service.provision(req))
                .isInstanceOf(TenantProvisioningException.class);

        // Current implementation only checks storageMode + dropOnFailure.
        verify(tenantProvisioner, times(1)).dropTenantDatabase("dbdisabled");
    }

}

