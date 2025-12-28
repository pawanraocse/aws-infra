package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global filter for API key authentication.
 * 
 * <p>
 * If request contains X-API-Key header, validates the key via platform-service
 * and injects tenant/user headers. If key is invalid, returns 401.
 * If no API key header present, continues to JWT authentication.
 * </p>
 * 
 * <p>
 * Order is set to run BEFORE JWT authentication filter (high priority = low
 * order number).
 * </p>
 */
@Slf4j
@Component
public class ApiKeyAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTH_METHOD_HEADER = "X-Auth-Method";
    private static final int ORDER = -100; // Run before JWT filter

    private final WebClient webClient;
    private final String platformServiceUrl;

    public ApiKeyAuthenticationFilter(
            WebClient.Builder webClientBuilder,
            @Value("${platform.service.url:http://platform-service:8083}") String platformServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.platformServiceUrl = platformServiceUrl;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        // No API key - continue to JWT authentication
        if (apiKey == null || apiKey.isBlank()) {
            return chain.filter(exchange);
        }

        log.debug("API key authentication attempt: prefix={}",
                apiKey.substring(0, Math.min(20, apiKey.length())));

        return validateApiKey(apiKey)
                .flatMap(result -> {
                    if (!result.valid()) {
                        log.debug("API key validation failed: error={}", result.errorCode());
                        return writeError(exchange, result.errorCode());
                    }

                    // Inject headers and continue
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-Tenant-Id", result.tenantId())
                            .header("X-User-Id", result.userId())
                            .header("X-User-Email", result.userEmail())
                            .header(AUTH_METHOD_HEADER, "api-key")
                            .header("X-Api-Key-Id", result.keyId())
                            // Remove the API key header before forwarding (security)
                            .headers(h -> h.remove(API_KEY_HEADER))
                            .build();

                    log.debug("API key authenticated: tenant={}, user={}",
                            result.tenantId(), result.userId());

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(e -> {
                    log.error("API key validation error: {}", e.getMessage());
                    return writeError(exchange, "API_KEY_VALIDATION_ERROR");
                });
    }

    private Mono<ApiKeyValidationResult> validateApiKey(String apiKey) {
        return webClient.get()
                .uri(platformServiceUrl + "/platform/internal/api-keys/validate?key={key}", apiKey)
                .retrieve()
                .bodyToMono(ApiKeyValidationResult.class)
                .doOnError(e -> log.error("Failed to validate API key: {}", e.getMessage()));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, String errorCode) {
        HttpStatus status = switch (errorCode) {
            case "API_KEY_EXPIRED" -> HttpStatus.UNAUTHORIZED;
            case "API_KEY_REVOKED" -> HttpStatus.UNAUTHORIZED;
            case "API_KEY_INVALID" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        String errorMessage = switch (errorCode) {
            case "API_KEY_EXPIRED" -> "API key has expired";
            case "API_KEY_REVOKED" -> "API key has been revoked";
            case "API_KEY_INVALID" -> "Invalid API key";
            default -> "API key validation failed";
        };

        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"code\":\"%s\",\"message\":\"%s\"}",
                java.time.Instant.now(),
                status.value(),
                errorCode,
                errorMessage);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * DTO for platform-service validation response.
     */
    public record ApiKeyValidationResult(
            String keyId,
            String tenantId,
            String userId,
            String userEmail,
            Integer rateLimitPerMinute,
            boolean valid,
            String errorCode) {
    }
}
