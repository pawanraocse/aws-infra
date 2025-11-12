package com.learning.gateway.support;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.function.Function;

/**
 * Provides shared utilities for exercising Gateway {@link org.springframework.cloud.gateway.filter.GlobalFilter}
 * and {@link org.springframework.cloud.gateway.filter.GatewayFilter} implementations without duplicating mock setup.
 */
public abstract class BaseGatewayFilterTest {

    protected MockServerHttpRequest.BaseBuilder<?> get(String path) {
        return MockServerHttpRequest.get(path);
    }

    protected MockServerHttpRequest.BodyBuilder post(String path) {
        return MockServerHttpRequest.post(path);
    }

    protected ServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    protected TestGatewayFilterChain chain() {
        return chain(exchange -> Mono.empty());
    }

    protected TestGatewayFilterChain chain(Function<ServerWebExchange, Mono<Void>> delegate) {
        return new TestGatewayFilterChain(delegate);
    }

    protected String responseBody(ServerWebExchange exchange) {
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        return response.getBodyAsString().block();
    }

    protected static final class TestGatewayFilterChain implements GatewayFilterChain {
        private final Function<ServerWebExchange, Mono<Void>> delegate;
        private ServerWebExchange lastExchange;

        private TestGatewayFilterChain(Function<ServerWebExchange, Mono<Void>> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.lastExchange = exchange;
            return delegate.apply(exchange);
        }

        public ServerHttpRequest lastRequest() {
            return lastExchange != null ? lastExchange.getRequest() : null;
        }

        public ServerWebExchange lastExchange() {
            return lastExchange;
        }
    }
}

