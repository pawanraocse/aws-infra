package com.learning.common.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caffeine-backed implementation of CacheService.
 * Used for local (non-distributed) caches.
 * 
 * <p>
 * Use cases:
 * </p>
 * <ul>
 * <li>Local-only caches like tenantConfig</li>
 * <li>Test environments without Redis</li>
 * <li>Fallback when Redis is unavailable</li>
 * </ul>
 */
@Slf4j
@Service
@Profile("test")
public class CaffeineCacheService implements CacheService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final long DEFAULT_MAX_SIZE = 1000;

    private final ConcurrentMap<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        Cache<String, Object> cache = getOrCreateCache(cacheName);
        Object value = cache.getIfPresent(key);

        if (value == null) {
            log.debug("Cache miss: {}:{}", cacheName, key);
            return Optional.empty();
        }

        log.debug("Cache hit: {}:{}", cacheName, key);
        return Optional.of((T) value);
    }

    @Override
    public void put(String cacheName, String key, Object value) {
        put(cacheName, key, value, DEFAULT_TTL);
    }

    @Override
    public void put(String cacheName, String key, Object value, Duration ttl) {
        // Note: Caffeine doesn't support per-entry TTL, using cache-level TTL
        Cache<String, Object> cache = getOrCreateCache(cacheName);
        cache.put(key, value);
        log.debug("Cache put: {}:{}", cacheName, key);
    }

    @Override
    public void evict(String cacheName, String key) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
            log.debug("Cache evict: {}:{}", cacheName, key);
        }
    }

    @Override
    public void evictAll(String cacheName) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
            log.debug("Cache evictAll: {}", cacheName);
        }
    }

    @Override
    public Set<String> keys(String cacheName) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            return cache.asMap().keySet();
        }
        return Set.of();
    }

    @Override
    public boolean isAvailable() {
        return true; // Local cache is always available
    }

    private Cache<String, Object> getOrCreateCache(String cacheName) {
        return caches.computeIfAbsent(cacheName, name -> Caffeine.newBuilder()
                .expireAfterWrite(DEFAULT_TTL)
                .maximumSize(DEFAULT_MAX_SIZE)
                .recordStats()
                .build());
    }
}
