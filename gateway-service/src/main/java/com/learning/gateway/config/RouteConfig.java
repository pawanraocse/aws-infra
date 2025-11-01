package com.learning.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("Configuring custom routes");

        return builder.routes()
                // Auth Service routes
                .route("auth-service", r -> r
                        .path("/auth/**")
                        .filters(f -> f
                                .rewritePath("/auth/(?<segment>.*)", "/${segment}")
                                .circuitBreaker(config -> config
                                        .setName("authServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setStatuses(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("lb://auth-service"))

                // Backend Service routes
                .route("backend-service", r -> r
                        .path("/api/**")
                        .filters(f -> f
                                .filter(new com.learning.gateway.filter.JwtAuthenticationGatewayFilterFactory().apply(
                                        new com.learning.gateway.filter.JwtAuthenticationGatewayFilterFactory.Config()))
                                .circuitBreaker(config -> config
                                        .setName("backendServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback"))
                                .retry(config -> config
                                        .setRetries(3)
                                        .setStatuses(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri("lb://backend-service"))

                .build();
    }
}
