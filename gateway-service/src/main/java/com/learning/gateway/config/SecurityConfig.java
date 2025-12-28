package com.learning.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

        @Value("${cors.allowed-origins:http://localhost:4200}")
        private String allowedOrigins;

        @Bean
        public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
                log.info("Configuring security filter chain");

                http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .authorizeExchange(exchange -> exchange
                                                .pathMatchers(
                                                                // Public auth endpoints (no JWT required)
                                                                "/auth/api/v1/auth/login",
                                                                "/auth/api/v1/auth/signup/**",
                                                                "/auth/api/v1/auth/tokens", "/auth/api/v1/auth/logout",
                                                                "/auth/api/v1/auth/verify",
                                                                "/auth/api/v1/auth/resend-verification",
                                                                // Forgot password endpoints
                                                                "/auth/api/v1/auth/forgot-password",
                                                                "/auth/api/v1/auth/reset-password",
                                                                // Multi-tenant login endpoints
                                                                "/auth/api/v1/auth/lookup",
                                                                "/auth/api/v1/auth/last-accessed",
                                                                "/auth/.well-known/**", "/auth/oauth2/**",
                                                                // Invitation acceptance (public)
                                                                "/auth/api/v1/invitations/validate",
                                                                "/auth/api/v1/invitations/accept",
                                                                // Stripe webhook (secured by signature, not JWT)
                                                                "/platform-service/platform/billing/webhook",
                                                                // Frontend config endpoint (public - needed before
                                                                // auth)
                                                                "/api/config/**",
                                                                // System endpoints
                                                                "/actuator/**", "/fallback")
                                                .permitAll()
                                                // Allow API key authenticated requests (validated by
                                                // ApiKeyAuthenticationFilter)
                                                .matchers(ex -> ex.getRequest().getHeaders().containsKey("X-API-Key")
                                                                ? org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult
                                                                                .match()
                                                                : org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult
                                                                                .notMatch())
                                                .permitAll()
                                                .anyExchange().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                new JwtAuthenticationConverter())))
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(authenticationEntryPoint())
                                                .accessDeniedHandler(accessDeniedHandler()));

                return http.build();
        }

        private Mono<Void> writeJsonResponse(ServerWebExchange exchange, HttpStatus status, String code,
                        String message) {
                String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
                String body = String.format(
                                "{\"timestamp\":\"%s\",\"status\":%d,\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                                Instant.now(), status.value(), code, message, requestId != null ? requestId : "none");
                exchange.getResponse().setStatusCode(status);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return exchange.getResponse().writeWith(
                                Mono.fromSupplier(() -> exchange.getResponse().bufferFactory()
                                                .wrap(body.getBytes(StandardCharsets.UTF_8))));
        }

        @Bean
        public ServerAuthenticationEntryPoint authenticationEntryPoint() {
                return (exchange, ex) -> {
                        log.warn("Unauthorized access: uri={}, requestId={}",
                                        exchange.getRequest().getURI(),
                                        exchange.getRequest().getHeaders().getFirst("X-Request-Id"));
                        return writeJsonResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                        "Authentication required");
                };
        }

        @Bean
        public ServerAccessDeniedHandler accessDeniedHandler() {
                return (exchange, ex) -> {
                        log.warn("Access denied: uri={}, requestId={}",
                                        exchange.getRequest().getURI(),
                                        exchange.getRequest().getHeaders().getFirst("X-Request-Id"));
                        return writeJsonResponse(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
                };
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);

                log.info("CORS configured with allowed origins: {}", allowedOrigins);
                return source;
        }
}
