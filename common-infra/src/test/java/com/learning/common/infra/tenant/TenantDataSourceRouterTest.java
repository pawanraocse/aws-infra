package com.learning.common.infra.tenant;

import com.learning.common.dto.TenantDbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantDataSourceRouter.
 * 
 * Tests verify:
 * - Routing to default datasource when no tenant context
 * - Routing to DATABASE mode tenant datasource
 * - Routing to SHARED mode personal shared datasource
 * - Datasource caching and eviction
 */
@ExtendWith(MockitoExtension.class)
class TenantDataSourceRouterTest {

    @Mock
    private TenantRegistryService tenantRegistry;

    @Mock
    private DataSource defaultDataSource;

    @Mock
    private DataSource personalSharedDataSource;

    private TenantDataSourceRouter router;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Default DataSource Tests")
    class DefaultDataSourceTests {

        @BeforeEach
        void setUp() {
            router = new TenantDataSourceRouter(tenantRegistry, defaultDataSource);
        }

        @Test
        @DisplayName("Returns default datasource when no tenant context is set")
        void testDefaultDataSourceWhenNoTenantContext() {
            DataSource result = router.determineTargetDataSource();
            assertThat(result).isSameAs(defaultDataSource);
            verifyNoInteractions(tenantRegistry);
        }

        @Test
        @DisplayName("Throws exception when no tenant context and no default datasource")
        void testThrowsWhenNoTenantContextAndNoDefault() {
            TenantDataSourceRouter routerWithoutDefault = new TenantDataSourceRouter(tenantRegistry);
            assertThatThrownBy(routerWithoutDefault::determineTargetDataSource)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No tenant context");
        }

        @Test
        @DisplayName("Returns default datasource for SYSTEM tenant")
        void testSystemTenantUsesDefaultDataSource() {
            TenantContext.setCurrentTenant(TenantDataSourceRouter.SYSTEM_TENANT_ID);
            DataSource result = router.determineTargetDataSource();
            assertThat(result).isSameAs(defaultDataSource);
            verifyNoInteractions(tenantRegistry);
        }
    }

    @Nested
    @DisplayName("DATABASE Mode Tests")
    class DatabaseModeTests {

        @BeforeEach
        void setUp() {
            router = new TenantDataSourceRouter(tenantRegistry, defaultDataSource, personalSharedDataSource);
        }

        @Test
        @DisplayName("Creates and caches tenant datasource for DATABASE mode")
        void testCreatesTenantDataSourceForDatabaseMode() {
            String tenantId = "test-org-tenant";
            TenantContext.setCurrentTenant(tenantId);

            TenantDbConfig config = new TenantDbConfig(
                    "jdbc:postgresql://localhost:5432/t_test_org",
                    "test_user",
                    "test_password",
                    null,
                    "DATABASE");
            when(tenantRegistry.load(tenantId)).thenReturn(config);

            DataSource result = router.determineTargetDataSource();

            assertThat(result).isNotNull();
            assertThat(result).isNotSameAs(defaultDataSource);
            assertThat(result).isNotSameAs(personalSharedDataSource);
            // Called twice: once for storage mode check, once for datasource creation
            verify(tenantRegistry, times(2)).load(tenantId);
            assertThat(router.getActiveTenantCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Caches datasource on subsequent requests for same tenant")
        void testCachesDataSourceForSameTenant() {
            String tenantId = "test-tenant-456";
            TenantContext.setCurrentTenant(tenantId);

            TenantDbConfig config = new TenantDbConfig(
                    "jdbc:postgresql://localhost:5432/t_456",
                    "test_user",
                    "test_password",
                    null,
                    "DATABASE");
            when(tenantRegistry.load(tenantId)).thenReturn(config);

            DataSource first = router.determineTargetDataSource();
            DataSource second = router.determineTargetDataSource();

            assertThat(first).isSameAs(second);
            // Registry called twice (once per determineTargetDataSource to check storage mode)
            // but HikariDataSource created only once
            assertThat(router.getActiveTenantCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SHARED Mode Tests")
    class SharedModeTests {

        @BeforeEach
        void setUp() {
            router = new TenantDataSourceRouter(tenantRegistry, defaultDataSource, personalSharedDataSource);
        }

        @Test
        @DisplayName("Routes SHARED tenant to personalSharedDataSource")
        void testSharedTenantUsesPersonalSharedDataSource() {
            String tenantId = "personal-user-123";
            TenantContext.setCurrentTenant(tenantId);

            TenantDbConfig config = new TenantDbConfig(
                    "jdbc:postgresql://localhost:5432/personal_shared",
                    "shared_user",
                    "shared_password",
                    null,
                    "SHARED");
            when(tenantRegistry.load(tenantId)).thenReturn(config);

            DataSource result = router.determineTargetDataSource();

            assertThat(result).isSameAs(personalSharedDataSource);
            // SHARED tenants don't create cached datasources
            assertThat(router.getActiveTenantCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Multiple SHARED tenants share the same datasource")
        void testMultipleSharedTenantsShareDataSource() {
            TenantDbConfig sharedConfig = new TenantDbConfig(
                    "jdbc:postgresql://localhost:5432/personal_shared",
                    "shared_user",
                    "shared_password",
                    null,
                    "SHARED");

            // First personal tenant
            TenantContext.setCurrentTenant("personal-user-1");
            when(tenantRegistry.load("personal-user-1")).thenReturn(sharedConfig);
            DataSource ds1 = router.determineTargetDataSource();

            // Second personal tenant
            TenantContext.setCurrentTenant("personal-user-2");
            when(tenantRegistry.load("personal-user-2")).thenReturn(sharedConfig);
            DataSource ds2 = router.determineTargetDataSource();

            // Both should use the same shared datasource
            assertThat(ds1).isSameAs(personalSharedDataSource);
            assertThat(ds2).isSameAs(personalSharedDataSource);
            assertThat(ds1).isSameAs(ds2);

            // No cached datasources created for SHARED tenants
            assertThat(router.getActiveTenantCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Eviction Tests")
    class EvictionTests {

        @BeforeEach
        void setUp() {
            router = new TenantDataSourceRouter(tenantRegistry, defaultDataSource, personalSharedDataSource);
        }

        @Test
        @DisplayName("Evicts tenant datasource from cache")
        void testEvictsTenantDataSource() {
            String tenantId = "tenant-to-evict";
            TenantContext.setCurrentTenant(tenantId);

            TenantDbConfig config = new TenantDbConfig(
                    "jdbc:postgresql://localhost:5432/evict_db",
                    "test_user",
                    "test_password",
                    null,
                    "DATABASE");
            when(tenantRegistry.load(tenantId)).thenReturn(config);

            router.determineTargetDataSource();
            assertThat(router.getActiveTenantCount()).isEqualTo(1);

            router.evictTenantDataSource(tenantId);

            assertThat(router.getActiveTenantCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("TenantContext Tests")
    class TenantContextTests {

        @Test
        @DisplayName("TenantContext is correctly set and cleared")
        void testTenantContextSetAndClear() {
            assertThat(TenantContext.getCurrentTenant()).isNull();

            TenantContext.setCurrentTenant("context-test");
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("context-test");

            TenantContext.clear();
            assertThat(TenantContext.getCurrentTenant()).isNull();
        }
    }
}
