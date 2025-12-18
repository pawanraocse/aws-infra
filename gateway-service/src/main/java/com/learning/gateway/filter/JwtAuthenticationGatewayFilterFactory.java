package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final String TENANT_GROUP_PREFIX = "tenant_";
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Duration ROLE_LOOKUP_TIMEOUT = Duration.ofSeconds(2);

    private final WebClient webClient;
    private final String authServiceUrl;

    public JwtAuthenticationGatewayFilterFactory(
            WebClient.Builder webClientBuilder,
            @Value("${auth.service.url:http://auth-service:8081}") String authServiceUrl) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> {
                    Jwt jwt = authentication.getToken();
                    TenantExtractionResult tenantResult = extractTenantId(jwt);
                    if (!tenantResult.success()) {
                        log.debug("NT-01 deny userId={} code={} status={}", jwt.getSubject(), tenantResult.errorCode(),
                                tenantResult.errorStatus().value());
                        return writeError(exchange, tenantResult.errorStatus(), tenantResult.errorCode(),
                                tenantResult.errorMessage());
                    }
                    String tenantId = tenantResult.tenantId();
                    String userId = jwt.getSubject();
                    String username = jwt.getClaimAsString("username");
                    String email = jwt.getClaimAsString("email");
                    String authorities = authentication.getAuthorities().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","));

                    // Lookup role from auth-service
                    return lookupRole(userId, tenantId)
                            .map(role -> {
                                var builder = exchange.getRequest().mutate()
                                        .header("X-User-Id", userId)
                                        .header("X-Username", username != null ? username : "")
                                        .header("X-Email", email != null ? email : "")
                                        .header("X-Tenant-Id", tenantId)
                                        .header("X-Role", role);
                                if (!authorities.isBlank()) {
                                    builder.header("X-Authorities", authorities);
                                }

                                log.debug("NT-01 allow path={} userId={} tenantId={} role={}",
                                        exchange.getRequest().getPath(), userId, tenantId, role);
                                return exchange.mutate().request(builder.build()).build();
                            })
                            .flatMap(chain::filter);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    /**
     * Lookup user's role from auth-service.
     * Falls back to empty role if lookup fails.
     */
    private Mono<String> lookupRole(String userId, String tenantId) {
        String url = authServiceUrl + "/auth/internal/users/" + userId + "/role";
        log.debug("Looking up role: url={} tenantId={}", url, tenantId);

        return webClient.get()
                .uri(url)
                .header("X-Tenant-Id", tenantId) // Auth-service needs tenant context
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, String>>() {
                })
                .map(response -> {
                    String roleId = response.getOrDefault("roleId", "");
                    log.debug("Role lookup success: userId={} roleId={}", userId, roleId);
                    return roleId;
                })
                .timeout(ROLE_LOOKUP_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Role lookup failed for userId={}: {}", userId, e.getMessage());
                    return Mono.just(""); // Fallback to empty role
                });
    }

    private TenantExtractionResult extractTenantId(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null && !groups.isEmpty()) {
            List<String> tenantGroups = groups.stream()
                    .filter(g -> g != null && g.startsWith(TENANT_GROUP_PREFIX))
                    .toList();
            if (tenantGroups.size() > 1) {
                return TenantExtractionResult.error(HttpStatus.BAD_REQUEST, "TENANT_CONFLICT",
                        "Multiple tenant groups present");
            }
            if (tenantGroups.size() == 1) {
                String raw = tenantGroups.get(0).substring(TENANT_GROUP_PREFIX.length());
                if (!TENANT_ID_PATTERN.matcher(raw).matches()) {
                    return TenantExtractionResult.error(HttpStatus.BAD_REQUEST, "TENANT_INVALID_FORMAT",
                            "Tenant ID format invalid");
                }
                return TenantExtractionResult.success(raw);
            }
        }
        String tenantClaim = jwt.getClaimAsString("custom:tenant_id");
        if (tenantClaim == null) {
            tenantClaim = jwt.getClaimAsString("custom:tenantId");
        }
        if (tenantClaim == null) {
            tenantClaim = jwt.getClaimAsString("tenantId");
        }

        if (tenantClaim != null && !tenantClaim.isBlank()) {
            if (!TENANT_ID_PATTERN.matcher(tenantClaim).matches()) {
                return TenantExtractionResult.error(HttpStatus.BAD_REQUEST, "TENANT_INVALID_FORMAT",
                        "Tenant ID format invalid");
            }
            return TenantExtractionResult.success(tenantClaim);
        }
        return TenantExtractionResult.error(HttpStatus.FORBIDDEN, "TENANT_MISSING", "Tenant claim missing");
    }

    private Mono<Void> writeError(org.springframework.web.server.ServerWebExchange exchange,
            HttpStatus status, String code, String message) {
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, Boolean.TRUE);
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("Response already committed for code=" + code));
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String requestId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER))
                .orElse("none");
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                Instant.now(), status.value(), code, message.replace("\"", "\\\""), requestId);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
    }

    private record TenantExtractionResult(
            boolean success,
            String tenantId,
            HttpStatus errorStatus,
            String errorCode,
            String errorMessage) {

        static TenantExtractionResult success(String tenantId) {
            return new TenantExtractionResult(true, tenantId, null, null, null);
        }

        static TenantExtractionResult error(HttpStatus status, String code, String message) {
            return new TenantExtractionResult(false, null, status, code, message);
        }
    }
}
