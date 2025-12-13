package com.learning.common.infra.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.learning.common.dto.TenantDbConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Local cache for tenant database configurations.
 * Uses Caffeine cache with configurable TTL and max size.
 */
@Component
public class TenantLocalCache {

    public static final int MAX_EXPIRE_TIME_IN_MIN = 30;
    public static final int MAXIMUM_SIZE = 1000;

    private final Cache<String, TenantDbConfig> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(MAX_EXPIRE_TIME_IN_MIN))
            .maximumSize(MAXIMUM_SIZE)
            .build();

    /**
     * Get tenant config from cache, or load from supplier if not cached.
     */
    public TenantDbConfig get(String tenantId, Supplier<TenantDbConfig> loader) {
        return cache.get(tenantId, t -> loader.get());
    }

    /**
     * Invalidate a tenant's cached config.
     */
    public void invalidate(String tenantId) {
        cache.invalidate(tenantId);
    }

    /**
     * Invalidate all cached configs.
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
