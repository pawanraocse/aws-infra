package com.learning.common.infra.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Centralized cache configuration for all services.
 * 
 * Services automatically get this configuration via component scan of
 * common-infra.
 * All defined caches use Caffeine with 10-minute TTL and max 1000 entries.
 * 
 * To add a new cache:
 * 1. Add constant in CacheNames
 * 2. Add to cache names array in this class
 */
@Configuration
@EnableCaching
public class CommonCacheConfiguration {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final long DEFAULT_MAX_SIZE = 1000;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                // Permission caches (require distributed cache for multi-instance)
                CacheNames.PERMISSIONS,
                CacheNames.USER_PERMISSIONS,
                CacheNames.USER_ALL_PERMISSIONS,
                // Tenant caches (OK to be local)
                CacheNames.TENANT_CONFIG);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(DEFAULT_TTL)
                .maximumSize(DEFAULT_MAX_SIZE)
                .recordStats()); // Enable stats for monitoring

        return cacheManager;
    }
}
