package com.learning.common.infra.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid cache configuration for distributed and local caching.
 * 
 * <h2>Routing Strategy</h2>
 * <ul>
 * <li><b>DISTRIBUTED</b> (Redisson): permissions, userPermissions,
 * userAllPermissions</li>
 * <li><b>LOCAL</b> (Caffeine): tenantConfig</li>
 * </ul>
 * 
 * <p>
 * If Redisson is not available, falls back to Caffeine for all caches.
 * </p>
 */
@Slf4j
@Configuration
@EnableCaching
public class CommonCacheConfiguration {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final long DEFAULT_MAX_SIZE = 1000;

    /**
     * Primary cache manager with hybrid routing.
     * Uses Redisson for distributed caches when available, Caffeine for local
     * caches.
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            org.springframework.beans.factory.ObjectProvider<RedissonClient> redissonProvider) {

        RedissonClient redissonClient = redissonProvider.getIfAvailable();

        if (redissonClient != null && !redissonClient.isShutdown()) {
            log.info("✅ Redisson available - using hybrid caching (Redisson + Caffeine)");
            return createHybridCacheManager(redissonClient);
        } else {
            log.info("⚠️ Redisson not available - using Caffeine for all caches");
            return createCaffeineCacheManager();
        }
    }

    /**
     * Caffeine-only cache service for local caches.
     */
    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService caffeineCacheService() {
        log.info("Creating CaffeineCacheService for local caching");
        return new CaffeineCacheService();
    }

    private CacheManager createHybridCacheManager(RedissonClient redissonClient) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<Cache> caches = new ArrayList<>();

        // Redisson caches for distributed
        Map<String, org.redisson.spring.cache.CacheConfig> redissonCacheConfigs = new HashMap<>();
        for (String cacheName : CacheNames.DISTRIBUTED_CACHES) {
            org.redisson.spring.cache.CacheConfig config = new org.redisson.spring.cache.CacheConfig();
            config.setTTL(DEFAULT_TTL.toMillis());
            config.setMaxIdleTime(0); // No idle timeout
            redissonCacheConfigs.put(cacheName, config);
        }

        RedissonSpringCacheManager redissonCacheManager = new RedissonSpringCacheManager(redissonClient,
                redissonCacheConfigs);

        for (String cacheName : CacheNames.DISTRIBUTED_CACHES) {
            Cache redissonCache = redissonCacheManager.getCache(cacheName);
            if (redissonCache != null) {
                caches.add(redissonCache);
                log.debug("Registered DISTRIBUTED cache: {} (Redisson)", cacheName);
            }
        }

        // Caffeine caches for local
        for (String cacheName : CacheNames.LOCAL_CACHES) {
            CaffeineCache caffeineCache = new CaffeineCache(cacheName,
                    Caffeine.newBuilder()
                            .expireAfterWrite(DEFAULT_TTL)
                            .maximumSize(DEFAULT_MAX_SIZE)
                            .recordStats()
                            .build());
            caches.add(caffeineCache);
            log.debug("Registered LOCAL cache: {} (Caffeine)", cacheName);
        }

        cacheManager.setCaches(caches);
        cacheManager.afterPropertiesSet();
        return cacheManager;
    }

    private CacheManager createCaffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(CacheNames.all());
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(DEFAULT_TTL)
                .maximumSize(DEFAULT_MAX_SIZE)
                .recordStats());
        return cacheManager;
    }
}
