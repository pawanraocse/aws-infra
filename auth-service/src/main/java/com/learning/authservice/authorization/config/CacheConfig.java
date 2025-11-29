package com.learning.authservice.authorization.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration for authorization service.
 * Uses in-memory caching for permission checks to improve performance.
 * 
 * In production, consider using Redis for distributed caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure cache manager with permission caches.
     * TTL is handled at the cache level (5 minutes default).
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "userPermissions", // Cache for individual permission checks
                "userAllPermissions" // Cache for all user permissions
        );
    }
}
