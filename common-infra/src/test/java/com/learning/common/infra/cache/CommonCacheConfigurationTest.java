package com.learning.common.infra.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CommonCacheConfiguration.
 * Verifies that all expected caches are properly configured.
 */
class CommonCacheConfigurationTest {

    private final CommonCacheConfiguration config = new CommonCacheConfiguration();

    @Test
    void cacheManagerCreatesPermissionsCache() {
        CacheManager cacheManager = config.cacheManager();

        assertThat(cacheManager.getCache(CacheNames.PERMISSIONS))
                .as("Permissions cache should exist")
                .isNotNull();
    }

    @Test
    void cacheManagerCreatesTenantConfigCache() {
        CacheManager cacheManager = config.cacheManager();

        assertThat(cacheManager.getCache(CacheNames.TENANT_CONFIG))
                .as("Tenant config cache should exist")
                .isNotNull();
    }

    @Test
    void cacheNamesAreCorrectConstants() {
        // Verify the constants match expected values (prevents typos)
        assertThat(CacheNames.PERMISSIONS).isEqualTo("permissions");
        assertThat(CacheNames.TENANT_CONFIG).isEqualTo("tenantConfig");
    }
}
