package com.learning.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Global filter that validates tenant status before allowing requests.
 * Blocks requests if tenant is DELETED, DELETING, or SUSPENDED.
 * 
 * Uses Caffeine cache to minimize calls to platform-service.
 * TODO: Migrate to Redis cache for distributed consistency (Phase 6)
 */
@Slf4j
@Component
public class TenantStatusValidationFilter implements GlobalFilter, Ordered {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ACTIVE_STATUS = "ACTIVE";

    // Paths that don't require tenant status validation
    private static final Set<String> SKIP_PATHS = Set.of(
            "/auth/api/v1/auth/signup",
            "/auth/api/v1/auth/lookup",
            "/auth/api/v1/auth/login",
            "/auth/api/v1/auth/verify",
            "/auth/api/v1/auth/confirm-signup",
            "/auth/actuator",
            "/platform/internal",
            "/backend/actuator");

    private final WebClient webClient;
    private final Cache<String, String> tenantStatusCache;

    public TenantStatusValidationFilter(
            @Value("${services.platform-service.url:http://platform-service:8083}") String platformServiceUrl) {

        this.webClient = WebClient.builder()
                .baseUrl(platformServiceUrl)
                .build();

        // Cache tenant status for 5 minutes
        // TODO: Move to Redis for distributed caching (Phase 6)
        this.tenantStatusCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip validation for public/internal paths
        if (shouldSkipValidation(path)) {
            return chain.filter(exchange);
        }

        String tenantId = request.getHeaders().getFirst(TENANT_HEADER);

        // No tenant = let other filters handle (might be unauthenticated request)
        if (tenantId == null || tenantId.isBlank()) {
            return chain.filter(exchange);
        }

        // Check cache first
        String cachedStatus = tenantStatusCache.getIfPresent(tenantId);
        if (cachedStatus != null) {
            if (ACTIVE_STATUS.equals(cachedStatus)) {
                return chain.filter(exchange);
            } else {
                log.warn("Blocked request to inactive tenant (cached): tenantId={}, status={}", tenantId, cachedStatus);
                return rejectRequest(exchange, cachedStatus);
            }
        }

        // Fetch status from platform-service
        return fetchTenantStatus(tenantId)
                .flatMap(status -> {
                    tenantStatusCache.put(tenantId, status);

                    if (ACTIVE_STATUS.equals(status)) {
                        return chain.filter(exchange);
                    } else {
                        log.warn("Blocked request to inactive tenant: tenantId={}, status={}", tenantId, status);
                        return rejectRequest(exchange, status);
                    }
                })
                .onErrorResume(e -> {
                    // On error, allow request through (fail-open for availability)
                    // Platform-service might be down, don't block all traffic
                    log.error("Failed to fetch tenant status, allowing request: tenantId={}, error={}",
                            tenantId, e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private boolean shouldSkipValidation(String path) {
        return SKIP_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<String> fetchTenantStatus(String tenantId) {
        return webClient.get()
                .uri("/internal/tenants/{id}/status", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.getOrDefault("status", "UNKNOWN"))
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn("UNKNOWN");
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String status) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String message = switch (status) {
            case "DELETED" -> "This account has been deleted";
            case "DELETING" -> "This account is being deleted";
            case "SUSPENDED" -> "This account has been suspended";
            default -> "This account is not accessible";
        };

        String body = String.format("{\"error\":\"TENANT_INACTIVE\",\"status\":\"%s\",\"message\":\"%s\"}",
                status, message);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }

    /**
     * Evict tenant from cache (called when tenant status changes).
     * Can be exposed via actuator or internal API.
     */
    public void evictTenantFromCache(String tenantId) {
        tenantStatusCache.invalidate(tenantId);
        log.info("Evicted tenant from status cache: {}", tenantId);
    }

    @Override
    public int getOrder() {
        // Run after JWT validation but before routing
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
