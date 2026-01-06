package com.learning.common.infra.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter service for API endpoints.
 * 
 * Uses Resilience4j RateLimiter with per-user limiting.
 * Default: 10 requests per second per user.
 * 
 * Usage:
 * - if (!rateLimiter.tryAcquire("permission-api", userId)) { throw
 * TooManyRequestsException }
 */
@Component
@Slf4j
public class ApiRateLimiter {

    private final RateLimiterRegistry registry;
    private final Map<String, RateLimiter> perUserLimiters = new ConcurrentHashMap<>();

    // Default: 10 requests per second (burst), refill 10 per second
    private static final int DEFAULT_LIMIT_FOR_PERIOD = 10;
    private static final Duration DEFAULT_LIMIT_REFRESH_PERIOD = Duration.ofSeconds(1);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(100);

    public ApiRateLimiter() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(DEFAULT_LIMIT_FOR_PERIOD)
                .limitRefreshPeriod(DEFAULT_LIMIT_REFRESH_PERIOD)
                .timeoutDuration(DEFAULT_TIMEOUT)
                .build();

        this.registry = RateLimiterRegistry.of(defaultConfig);
        log.info("API rate limiter initialized: {} requests per {}",
                DEFAULT_LIMIT_FOR_PERIOD, DEFAULT_LIMIT_REFRESH_PERIOD);
    }

    /**
     * Try to acquire a rate limit permit for a specific user and endpoint.
     * 
     * @param endpointName Name of the endpoint (e.g., "permission-share")
     * @param userId       User making the request
     * @return true if permitted, false if rate limited
     */
    public boolean tryAcquire(String endpointName, String userId) {
        String key = endpointName + ":" + userId;
        RateLimiter limiter = perUserLimiters.computeIfAbsent(key,
                k -> registry.rateLimiter(k));

        boolean permitted = limiter.acquirePermission();

        if (!permitted) {
            log.warn("Rate limit exceeded: endpoint={}, user={}", endpointName, userId);
        }

        return permitted;
    }

    /**
     * Try to acquire with custom limits (for specific endpoints).
     * 
     * @param endpointName   Endpoint name
     * @param userId         User ID
     * @param limitForPeriod Max requests per period
     * @param refreshPeriod  Period duration
     * @return true if permitted
     */
    public boolean tryAcquire(String endpointName, String userId, int limitForPeriod, Duration refreshPeriod) {
        String key = endpointName + ":" + userId;

        RateLimiter limiter = perUserLimiters.computeIfAbsent(key, k -> {
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(limitForPeriod)
                    .limitRefreshPeriod(refreshPeriod)
                    .timeoutDuration(DEFAULT_TIMEOUT)
                    .build();
            return RateLimiter.of(k, config);
        });

        boolean permitted = limiter.acquirePermission();

        if (!permitted) {
            log.warn("Rate limit exceeded: endpoint={}, user={}, limit={}/{}",
                    endpointName, userId, limitForPeriod, refreshPeriod);
        }

        return permitted;
    }

    /**
     * Get rate limiter metrics for a user/endpoint.
     */
    public RateLimiterMetrics getMetrics(String endpointName, String userId) {
        String key = endpointName + ":" + userId;
        RateLimiter limiter = perUserLimiters.get(key);

        if (limiter == null) {
            return new RateLimiterMetrics(DEFAULT_LIMIT_FOR_PERIOD, DEFAULT_LIMIT_FOR_PERIOD);
        }

        var metrics = limiter.getMetrics();
        return new RateLimiterMetrics(
                metrics.getAvailablePermissions(),
                metrics.getNumberOfWaitingThreads());
    }

    public record RateLimiterMetrics(int availablePermissions, int waitingThreads) {
    }
}
