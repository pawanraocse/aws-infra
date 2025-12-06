package com.learning.authservice.cache;

import com.learning.common.dto.TenantDbConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Local in-memory cache for tenant database configurations.
 * Uses Caffeine cache with TTL and size limits.
 */
@Component
public class LocalCache {
    public static final int MAX_EXPIRE_TIME_IN_MIN = 30;
    public static final int MAXIMUM_SIZE = 1000;

    private final Cache<String, TenantDbConfig> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(MAX_EXPIRE_TIME_IN_MIN))
            .maximumSize(MAXIMUM_SIZE)
            .build();

    public TenantDbConfig get(String tenantId, Supplier<TenantDbConfig> loader) {
        return cache.get(tenantId, t -> loader.get());
    }

    public void evict(String tenantId) {
        cache.invalidate(tenantId);
    }
}
