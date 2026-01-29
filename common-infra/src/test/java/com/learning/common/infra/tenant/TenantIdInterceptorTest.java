package com.learning.common.infra.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TenantIdInterceptor.
 * 
 * Tests verify:
 * - Auto-injection of tenant_id on persist
 * - Validation of tenant_id on update  
 * - Cross-tenant update prevention
 */
class TenantIdInterceptorTest {

    private TenantIdInterceptor interceptor;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        interceptor = new TenantIdInterceptor();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // Test entity implementing TenantAwareEntity
    static class TestEntity implements TenantAwareEntity {
        private String tenantId;
        private String data;

        public TestEntity() {}

        public TestEntity(String tenantId, String data) {
            this.tenantId = tenantId;
            this.data = data;
        }

        @Override
        public String getTenantId() {
            return tenantId;
        }

        @Override
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    // Non-tenant-aware entity for testing skip behavior
    static class NonTenantEntity {
        private String data;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    @Nested
    @DisplayName("PrePersist Tests")
    class PrePersistTests {

        @Test
        @DisplayName("Sets tenant_id from TenantContext when not already set")
        void testSetsTenantIdFromContext() {
            TestEntity entity = new TestEntity();
            entity.setData("test data");
            TenantContext.setCurrentTenant("test-tenant-123");

            interceptor.setTenantIdOnCreate(entity);

            assertThat(entity.getTenantId()).isEqualTo("test-tenant-123");
        }

        @Test
        @DisplayName("Does not override existing tenant_id")
        void testDoesNotOverrideExistingTenantId() {
            TestEntity entity = new TestEntity("original-tenant", "test data");
            TenantContext.setCurrentTenant("different-tenant");

            interceptor.setTenantIdOnCreate(entity);

            assertThat(entity.getTenantId()).isEqualTo("original-tenant");
        }

        @Test
        @DisplayName("Handles null TenantContext gracefully")
        void testHandlesNullTenantContext() {
            TestEntity entity = new TestEntity();
            entity.setData("test data");
            // No tenant context set

            interceptor.setTenantIdOnCreate(entity);

            // Should not throw, tenant_id remains null
            assertThat(entity.getTenantId()).isNull();
        }

        @Test
        @DisplayName("Ignores non-TenantAwareEntity objects")
        void testIgnoresNonTenantAwareEntities() {
            NonTenantEntity entity = new NonTenantEntity();
            entity.setData("test data");
            TenantContext.setCurrentTenant("test-tenant");

            // Should not throw
            interceptor.setTenantIdOnCreate(entity);
        }
    }

    @Nested
    @DisplayName("PreUpdate Tests")
    class PreUpdateTests {

        @Test
        @DisplayName("Allows update when tenant_id matches context")
        void testAllowsUpdateWhenTenantMatches() {
            TestEntity entity = new TestEntity("test-tenant", "test data");
            TenantContext.setCurrentTenant("test-tenant");

            // Should not throw
            interceptor.validateTenantIdOnUpdate(entity);
        }

        @Test
        @DisplayName("Throws exception on cross-tenant update attempt")
        void testThrowsOnCrossTenantUpdate() {
            TestEntity entity = new TestEntity("tenant-A", "test data");
            TenantContext.setCurrentTenant("tenant-B");

            assertThatThrownBy(() -> interceptor.validateTenantIdOnUpdate(entity))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cross-tenant update attempted")
                    .hasMessageContaining("tenant-A")
                    .hasMessageContaining("tenant-B");
        }

        @Test
        @DisplayName("Allows update when entity tenant_id is null")
        void testAllowsUpdateWhenEntityTenantIsNull() {
            TestEntity entity = new TestEntity();
            entity.setData("test data");
            TenantContext.setCurrentTenant("test-tenant");

            // Should not throw - null entity tenant is allowed
            interceptor.validateTenantIdOnUpdate(entity);
        }

        @Test
        @DisplayName("Allows update when context tenant_id is null")
        void testAllowsUpdateWhenContextTenantIsNull() {
            TestEntity entity = new TestEntity("test-tenant", "test data");
            // No tenant context set

            // Should not throw - null context tenant is allowed
            interceptor.validateTenantIdOnUpdate(entity);
        }

        @Test
        @DisplayName("Ignores non-TenantAwareEntity objects")
        void testIgnoresNonTenantAwareEntities() {
            NonTenantEntity entity = new NonTenantEntity();
            entity.setData("test data");
            TenantContext.setCurrentTenant("test-tenant");

            // Should not throw
            interceptor.validateTenantIdOnUpdate(entity);
        }
    }
}
