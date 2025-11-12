package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.UUID;

/**
 * NT-03 RequestIdGlobalFilter
 * Ensures every request has an X-Request-Id header and propagates it via Reactor Context.
 */
@Slf4j
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CTX_KEY_REQUEST_ID = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = incoming != null && !incoming.isBlank() ? incoming : UUID.randomUUID().toString();

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        exchange.getAttributes().put(REQUEST_ID_HEADER, requestId);

        return chain.filter(exchange.mutate().request(mutated).build())
                .contextWrite(ctx -> ctx.put(CTX_KEY_REQUEST_ID, requestId));
    }

    @Override
    public int getOrder() {
        // Run after header sanitization but before JWT enrichment
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    public static String requestIdFromContext(ContextView ctx, String fallback) {
        return ctx.hasKey(CTX_KEY_REQUEST_ID) ? ctx.get(CTX_KEY_REQUEST_ID) : fallback;
    }
}

