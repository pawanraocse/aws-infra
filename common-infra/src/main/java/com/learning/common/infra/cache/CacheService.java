package com.learning.common.infra.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Cache service interface for SOLID design.
 * Allows swapping implementations (Lettuce, Redisson, etc.) without changing
 * callers.
 * 
 * <p>
 * Use case examples:
 * </p>
 * <ul>
 * <li>Production: RedisCacheService (distributed)</li>
 * <li>Tests: CaffeineCacheService or mock</li>
 * </ul>
 */
public interface CacheService {

    /**
     * Get a cached value.
     *
     * @param cacheName the cache namespace
     * @param key       the cache key
     * @param type      expected value type
     * @return Optional containing the value if present
     */
    <T> Optional<T> get(String cacheName, String key, Class<T> type);

    /**
     * Put a value in cache with default TTL.
     *
     * @param cacheName the cache namespace
     * @param key       the cache key
     * @param value     the value to cache
     */
    void put(String cacheName, String key, Object value);

    /**
     * Put a value in cache with custom TTL.
     *
     * @param cacheName the cache namespace
     * @param key       the cache key
     * @param value     the value to cache
     * @param ttl       time-to-live duration
     */
    void put(String cacheName, String key, Object value, Duration ttl);

    /**
     * Evict a single key from cache.
     *
     * @param cacheName the cache namespace
     * @param key       the cache key
     */
    void evict(String cacheName, String key);

    /**
     * Evict all entries from a cache.
     *
     * @param cacheName the cache namespace
     */
    void evictAll(String cacheName);

    /**
     * Get all keys in a cache (for debugging/monitoring).
     *
     * @param cacheName the cache namespace
     * @return set of keys
     */
    Set<String> keys(String cacheName);

    /**
     * Check if cache is available and connected.
     *
     * @return true if cache is operational
     */
    boolean isAvailable();
}
