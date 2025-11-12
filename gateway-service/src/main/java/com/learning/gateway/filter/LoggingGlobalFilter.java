package com.learning.gateway.filter;

// NT-03: Deprecated. Replaced by EnhancedLoggingGlobalFilter. Keeping class stub for history.
// Removed @Component annotation to disable registration.
// Previous implementation removed to avoid duplicate logs.

public class LoggingGlobalFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, org.springframework.core.Ordered {
    @Override
    public reactor.core.publisher.Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        // No-op
        return chain.filter(exchange);
    }
    @Override
    public int getOrder() { return org.springframework.core.Ordered.LOWEST_PRECEDENCE; }
}
