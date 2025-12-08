package com.learning.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration for the gateway.
 * Provides key resolvers for Redis-based rate limiting.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Key resolver that rate limits by tenant ID.
     * This ensures each tenant gets their own rate limit bucket.
     * Falls back to IP address for unauthenticated requests.
     */
    @Bean
    @Primary
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            // Try to get tenant ID from X-Tenant-Id header (set by JwtAuthentication
            // filter)
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
            if (tenantId != null && !tenantId.isEmpty()) {
                return Mono.just("tenant:" + tenantId);
            }

            // Fallback to IP address for unauthenticated requests (signup, login, etc.)
            String clientIp = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + clientIp);
        };
    }

    /**
     * Alternative key resolver that rate limits by user ID.
     * Useful for stricter per-user rate limiting.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("anonymous");
        };
    }

    /**
     * IP-based key resolver for endpoints that don't require authentication.
     * Primarily for protecting public endpoints like login and signup from abuse.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String clientIp = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + clientIp);
        };
    }
}
