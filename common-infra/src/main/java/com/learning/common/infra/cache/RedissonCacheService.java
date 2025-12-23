package com.learning.common.infra.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Redisson-backed implementation of CacheService.
 * Provides distributed caching with support for distributed locks.
 * 
 * <p>
 * Key format: {cacheName}:{key}
 * </p>
 * 
 * <p>
 * Advantages over Lettuce:
 * </p>
 * <ul>
 * <li>Distributed locks (RLock)</li>
 * <li>Distributed collections (RMap, RSet)</li>
 * <li>Pub/Sub messaging</li>
 * <li>Automatic reconnection</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class RedissonCacheService implements CacheService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final String KEY_SEPARATOR = ":";

    private final RedissonClient redissonClient;

    @Override
    public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        try {
            String fullKey = buildKey(cacheName, key);
            RBucket<T> bucket = redissonClient.getBucket(fullKey);
            T value = bucket.get();

            if (value == null) {
                log.debug("Cache miss: {}", fullKey);
                return Optional.empty();
            }

            log.debug("Cache hit: {}", fullKey);
            return Optional.of(value);
        } catch (Exception e) {
            log.warn("Cache get failed: {}:{} - {}", cacheName, key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String cacheName, String key, Object value) {
        put(cacheName, key, value, DEFAULT_TTL);
    }

    @Override
    public void put(String cacheName, String key, Object value, Duration ttl) {
        try {
            String fullKey = buildKey(cacheName, key);
            RBucket<Object> bucket = redissonClient.getBucket(fullKey);
            bucket.set(value, ttl);
            log.debug("Cache put: {} (TTL: {})", fullKey, ttl);
        } catch (Exception e) {
            log.warn("Cache put failed: {}:{} - {}", cacheName, key, e.getMessage());
        }
    }

    @Override
    public void evict(String cacheName, String key) {
        try {
            String fullKey = buildKey(cacheName, key);
            RBucket<Object> bucket = redissonClient.getBucket(fullKey);
            boolean deleted = bucket.delete();
            log.debug("Cache evict: {} (deleted: {})", fullKey, deleted);
        } catch (Exception e) {
            log.warn("Cache evict failed: {}:{} - {}", cacheName, key, e.getMessage());
        }
    }

    @Override
    public void evictAll(String cacheName) {
        try {
            String pattern = cacheName + KEY_SEPARATOR + "*";
            RKeys keys = redissonClient.getKeys();
            long deleted = keys.deleteByPattern(pattern);
            log.debug("Cache evictAll: {} keys deleted from {}", deleted, cacheName);
        } catch (Exception e) {
            log.warn("Cache evictAll failed: {} - {}", cacheName, e.getMessage());
        }
    }

    @Override
    public Set<String> keys(String cacheName) {
        try {
            String pattern = cacheName + KEY_SEPARATOR + "*";
            RKeys rKeys = redissonClient.getKeys();
            Set<String> result = new HashSet<>();
            rKeys.getKeysByPattern(pattern).forEach(result::add);
            return result;
        } catch (Exception e) {
            log.warn("Cache keys failed: {} - {}", cacheName, e.getMessage());
            return Set.of();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if Redisson is connected
            return !redissonClient.isShutdown();
        } catch (Exception e) {
            log.warn("Redisson not available: {}", e.getMessage());
            return false;
        }
    }

    private String buildKey(String cacheName, String key) {
        return cacheName + KEY_SEPARATOR + key;
    }
}
