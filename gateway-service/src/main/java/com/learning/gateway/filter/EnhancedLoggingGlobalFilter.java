package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * NT-03 EnhancedLoggingGlobalFilter
 * Emits a single structured log line at request completion with timing and identity context (if available).
 * Runs last.
 */
@Slf4j
@Component
public class EnhancedLoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startMillis = System.currentTimeMillis();
        return chain.filter(exchange)
                .doOnError(err -> logError(exchange, startMillis, err))
                .doOnSuccess(v -> logSuccess(exchange, startMillis));
    }

    private void logSuccess(ServerWebExchange exchange, long startMillis) {
        long durationMs = System.currentTimeMillis() - startMillis;
        String requestId = header(exchange, "X-Request-Id");
        String userId = header(exchange, "X-User-Id");
        String tenantId = header(exchange, "X-Tenant-Id");
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        int status = exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : 0;
        log.info("gateway_log {{\"event\":\"request_completed\",\"ts\":\"{}\",\"requestId\":\"{}\",\"userId\":\"{}\",\"tenantId\":\"{}\",\"method\":\"{}\",\"path\":\"{}\",\"status\":{},\"durationMs\":{} }}",
                Instant.now(), requestId, userId, tenantId, method, path, status, durationMs);
    }

    private void logError(ServerWebExchange exchange, long startMillis, Throwable err) {
        long durationMs = System.currentTimeMillis() - startMillis;
        String requestId = header(exchange, "X-Request-Id");
        String userId = header(exchange, "X-User-Id");
        String tenantId = header(exchange, "X-Tenant-Id");
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        log.warn("gateway_log {{\"event\":\"request_error\",\"ts\":\"{}\",\"requestId\":\"{}\",\"userId\":\"{}\",\"tenantId\":\"{}\",\"method\":\"{}\",\"path\":\"{}\",\"error\":\"{}\",\"durationMs\":{} }}",
                Instant.now(), requestId, userId, tenantId, method, path, err.getClass().getSimpleName(), durationMs);
    }

    private String header(ServerWebExchange exchange, String name) {
        String v = exchange.getRequest().getHeaders().getFirst(name);
        return v == null ? "" : v;
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}

