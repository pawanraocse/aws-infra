package com.learning.common.infra.tenant;

import com.learning.common.dto.TenantDbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * <p>
 * These tests verify:
 * </p>
 * <ul>
 * <li>Routing to default datasource when no tenant context is set</li>
 * <li>Routing to tenant-specific datasource when tenant context is set</li>
 * <li>Datasource caching behavior</li>
 * <li>Eviction of tenant datasources</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenantDataSourceRouterTest {

    @Mock
    private TenantRegistryService tenantRegistry;

    @Mock
    private DataSource defaultDataSource;

    private TenantDataSourceRouter router;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        router = new TenantDataSourceRouter(tenantRegistry, defaultDataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Returns default datasource when no tenant context is set")
    void testDefaultDataSourceWhenNoTenantContext() {
        // Arrange - no tenant context set

        // Act
        DataSource result = router.determineTargetDataSource();

        // Assert
        assertThat(result).isSameAs(defaultDataSource);
        verifyNoInteractions(tenantRegistry);
    }

    @Test
    @DisplayName("Throws exception when no tenant context and no default datasource")
    void testThrowsWhenNoTenantContextAndNoDefault() {
        // Arrange - router without default datasource
        TenantDataSourceRouter routerWithoutDefault = new TenantDataSourceRouter(tenantRegistry);

        // Act & Assert
        assertThatThrownBy(routerWithoutDefault::determineTargetDataSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant context");
    }

    @Test
    @DisplayName("Creates and caches tenant datasource on first request")
    void testCreatesTenantDataSourceOnFirstRequest() {
        // Arrange
        String tenantId = "test-tenant-123";
        TenantContext.setCurrentTenant(tenantId);

        TenantDbConfig mockConfig = new TenantDbConfig(
                "jdbc:postgresql://localhost:5432/test_db",
                "test_user",
                "test_password");
        when(tenantRegistry.load(tenantId)).thenReturn(mockConfig);

        // Act
        DataSource result = router.determineTargetDataSource();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isNotSameAs(defaultDataSource);
        verify(tenantRegistry, times(1)).load(tenantId);
        assertThat(router.getActiveTenantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Caches datasource on subsequent requests for same tenant")
    void testCachesDataSourceForSameTenant() {
        // Arrange
        String tenantId = "test-tenant-456";
        TenantContext.setCurrentTenant(tenantId);

        TenantDbConfig mockConfig = new TenantDbConfig(
                "jdbc:postgresql://localhost:5432/test_db_456",
                "test_user",
                "test_password");
        when(tenantRegistry.load(tenantId)).thenReturn(mockConfig);

        // Act - call twice
        DataSource first = router.determineTargetDataSource();
        DataSource second = router.determineTargetDataSource();

        // Assert - should be same instance, registry called only once
        assertThat(first).isSameAs(second);
        verify(tenantRegistry, times(1)).load(tenantId);
        assertThat(router.getActiveTenantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Creates different datasources for different tenants")
    void testDifferentDataSourcesForDifferentTenants() {
        // Arrange
        String tenant1 = "tenant-one";
        String tenant2 = "tenant-two";

        TenantDbConfig config1 = new TenantDbConfig(
                "jdbc:postgresql://localhost:5432/db_one",
                "user_one",
                "pass_one");
        TenantDbConfig config2 = new TenantDbConfig(
                "jdbc:postgresql://localhost:5432/db_two",
                "user_two",
                "pass_two");

        when(tenantRegistry.load(tenant1)).thenReturn(config1);
        when(tenantRegistry.load(tenant2)).thenReturn(config2);

        // Act
        TenantContext.setCurrentTenant(tenant1);
        DataSource ds1 = router.determineTargetDataSource();

        TenantContext.setCurrentTenant(tenant2);
        DataSource ds2 = router.determineTargetDataSource();

        // Assert
        assertThat(ds1).isNotSameAs(ds2);
        assertThat(router.getActiveTenantCount()).isEqualTo(2);
        verify(tenantRegistry).load(tenant1);
        verify(tenantRegistry).load(tenant2);
    }

    @Test
    @DisplayName("Evicts tenant datasource from cache")
    void testEvictsTenantDataSource() {
        // Arrange
        String tenantId = "tenant-to-evict";
        TenantContext.setCurrentTenant(tenantId);

        TenantDbConfig mockConfig = new TenantDbConfig(
                "jdbc:postgresql://localhost:5432/evict_db",
                "test_user",
                "test_password");
        when(tenantRegistry.load(tenantId)).thenReturn(mockConfig);

        // Create datasource
        router.determineTargetDataSource();
        assertThat(router.getActiveTenantCount()).isEqualTo(1);

        // Act - evict
        router.evictTenantDataSource(tenantId);

        // Assert
        assertThat(router.getActiveTenantCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("TenantContext is correctly set and cleared")
    void testTenantContextSetAndClear() {
        // Arrange
        String tenantId = "context-test";

        // Act & Assert - initially null
        assertThat(TenantContext.getCurrentTenant()).isNull();

        // Set tenant
        TenantContext.setCurrentTenant(tenantId);
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(tenantId);

        // Clear tenant
        TenantContext.clear();
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
