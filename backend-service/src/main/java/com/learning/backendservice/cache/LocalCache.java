package com.learning.backendservice.cache;

import com.learning.common.dto.TenantDbConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

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

    public void invalidate(String tenantId) {
        cache.invalidate(tenantId);
    }
}
