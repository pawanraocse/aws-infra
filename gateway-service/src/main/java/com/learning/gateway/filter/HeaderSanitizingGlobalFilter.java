package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * NT-02 HeaderSanitizingGlobalFilter
 * Removes any inbound spoofable identity / authorization headers before JWT
 * processing.
 * 
 * <p>
 * Note: X-Role removed from this list - gateway no longer sets X-Role header.
 * Downstream services now lookup roles directly from the database.
 * </p>
 * 
 * Runs at highest precedence. Controlled by feature flag
 * security.gateway.sanitize-headers (default true).
 */
@Slf4j
@Component
public class HeaderSanitizingGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> SPOOFABLE_HEADERS = Arrays.asList(
            "X-User-Id",
            "X-Username",
            "X-Email",
            "X-Tenant-Id",
            "X-Authorities",
            "X-Auth-Signature");

    @Value("${security.gateway.sanitize-headers:true}")
    private boolean sanitizeEnabled;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (!sanitizeEnabled) {
            return chain.filter(exchange); // Feature flag off
        }
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(httpHeaders -> {
                    SPOOFABLE_HEADERS.forEach(h -> {
                        if (httpHeaders.containsKey(h)) {
                            httpHeaders.remove(h);
                        }
                    });
                })
                .build();
        if (log.isTraceEnabled()) {
            log.trace("NT-02 sanitized headers for path={}", exchange.getRequest().getPath());
        }
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // Highest precedence to run before all other filters
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
