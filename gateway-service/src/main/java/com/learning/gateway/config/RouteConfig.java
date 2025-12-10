package com.learning.gateway.config;

import com.learning.gateway.filter.JwtAuthenticationGatewayFilterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class RouteConfig {

        // === Route and filter constants ===
        private static final String AUTH_SERVICE_ID = "auth-service";
        private static final String BACKEND_SERVICE_ID = "backend-service";
        private static final String PLATFORM_SERVICE_ID = "platform-service";
        private static final String FALLBACK_URI = "forward:/fallback";
        private static final String AUTH_PATH = "/auth/**";
        private static final String API_PATH = "/api/**";
        private static final String PLATFORM_PATH = "/platform/**";
        private static final String CB_AUTH = "authServiceCircuitBreaker";
        private static final String CB_BACKEND = "backendServiceCircuitBreaker";
        private static final String CB_PLATFORM = "platformServiceCircuitBreaker";

        private final JwtAuthenticationGatewayFilterFactory jwtFilterFactory;

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                log.info("Configuring custom routes");

                return builder.routes()
                                .route(AUTH_SERVICE_ID, r -> r
                                                .path(AUTH_PATH)
                                                .filters(f -> f
                                                                .filter(jwtFilterFactory.apply(
                                                                                new JwtAuthenticationGatewayFilterFactory.Config()))
                                                                .preserveHostHeader()
                                                                .circuitBreaker(c -> c
                                                                                .setName(CB_AUTH)
                                                                                .setFallbackUri(FALLBACK_URI))
                                                                .retry(rCfg -> rCfg
                                                                                .setRetries(3)
                                                                                .setStatuses(HttpStatus.BAD_GATEWAY,
                                                                                                HttpStatus.SERVICE_UNAVAILABLE)))
                                                .uri("lb://" + AUTH_SERVICE_ID))

                                .route(PLATFORM_SERVICE_ID, r -> r
                                                .path(PLATFORM_PATH)
                                                .filters(f -> f
                                                                .filter(jwtFilterFactory.apply(
                                                                                new JwtAuthenticationGatewayFilterFactory.Config()))
                                                                .circuitBreaker(c -> c
                                                                                .setName(CB_PLATFORM)
                                                                                .setFallbackUri(FALLBACK_URI))
                                                                .retry(rCfg -> rCfg
                                                                                .setRetries(3)
                                                                                .setStatuses(HttpStatus.BAD_GATEWAY,
                                                                                                HttpStatus.SERVICE_UNAVAILABLE)))
                                                .uri("lb://" + PLATFORM_SERVICE_ID))

                                .route(BACKEND_SERVICE_ID, r -> r
                                                .path(API_PATH)
                                                .filters(f -> f
                                                                .filter(jwtFilterFactory.apply(
                                                                                new JwtAuthenticationGatewayFilterFactory.Config()))
                                                                .circuitBreaker(c -> c
                                                                                .setName(CB_BACKEND)
                                                                                .setFallbackUri(FALLBACK_URI))
                                                                .retry(rCfg -> rCfg
                                                                                .setRetries(3)
                                                                                .setStatuses(HttpStatus.BAD_GATEWAY,
                                                                                                HttpStatus.SERVICE_UNAVAILABLE)))
                                                .uri("lb://" + BACKEND_SERVICE_ID))

                                .build();
        }
}
