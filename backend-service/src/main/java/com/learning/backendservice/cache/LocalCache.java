package com.learning.backendservice.cache;

import com.learning.common.dto.TenantDbInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class LocalCache {
    public static final int MAX_EXPIRE_TIME_IN_MIN = 30;
    public static final int MAXIMUM_SIZE = 1000;
    private final Cache<String, TenantDbInfo> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(MAX_EXPIRE_TIME_IN_MIN))
            .maximumSize(MAXIMUM_SIZE)
            .build();

    public TenantDbInfo get(String tenantId, Supplier<TenantDbInfo> loader) {
        return cache.get(tenantId, t -> loader.get());
    }

    public void invalidate(String tenantId) {
        cache.invalidate(tenantId);
    }
}

