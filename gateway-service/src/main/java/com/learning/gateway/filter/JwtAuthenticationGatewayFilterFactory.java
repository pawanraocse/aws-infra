package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JWT Authentication filter that extracts user/tenant info from JWT token.
 * 
 * <p>
 * Role lookup has been removed from gateway - downstream services now
 * lookup roles directly from the database via RoleLookupService for better
 * security.
 * </p>
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final String TENANT_GROUP_PREFIX = "tenant_";
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    public JwtAuthenticationGatewayFilterFactory() {
        super(Config.class);
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

                    // Debug: log all claims to troubleshoot email extraction
                    log.debug("JWT claims for userId={}: {}", userId, jwt.getClaims().keySet());

                    // Extract email - check multiple claim locations for SSO compatibility
                    String email = jwt.getClaimAsString("email");
                    if (email == null || email.isBlank()) {
                        // For SSO users, email may be in custom claims
                        email = jwt.getClaimAsString("custom:email");
                    }
                    if (email == null || email.isBlank()) {
                        // For federated users, extract from identities claim
                        // identities is an array of objects: [{userId: "email", providerName: "...",
                        // ...}]
                        Object identitiesObj = jwt.getClaim("identities");
                        if (identitiesObj instanceof java.util.List<?> identitiesList && !identitiesList.isEmpty()) {
                            Object firstIdentity = identitiesList.get(0);
                            if (firstIdentity instanceof java.util.Map<?, ?> identityMap) {
                                Object userIdObj = identityMap.get("userId");
                                if (userIdObj instanceof String userIdStr && userIdStr.contains("@")) {
                                    email = userIdStr;
                                    log.debug("Extracted email from identities: {}", email);
                                }
                            }
                        }
                    }
                    if (email == null || email.isBlank()) {
                        // Last resort: extract from cognito:username (format: "prefix_email@domain")
                        String cognitoUsername = jwt.getClaimAsString("cognito:username");
                        if (cognitoUsername != null && cognitoUsername.contains("@")) {
                            // Extract email part after underscore (e.g., "okta-aarohan_user@example.com")
                            int underscoreIdx = cognitoUsername.indexOf('_');
                            if (underscoreIdx > 0 && underscoreIdx < cognitoUsername.length() - 1) {
                                email = cognitoUsername.substring(underscoreIdx + 1);
                            } else {
                                email = cognitoUsername;
                            }
                            log.debug("Extracted email from cognito:username: {}", email);
                        }
                    }

                    String authorities = authentication.getAuthorities().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","));

                    // NOTE: Role lookup removed - downstream services now lookup roles directly
                    // from the database via RoleLookupService for better security
                    var builder = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-Username", username != null ? username : "")
                            .header("X-Email", email != null ? email : "")
                            .header("X-Tenant-Id", tenantId);
                    if (!authorities.isBlank()) {
                        builder.header("X-Authorities", authorities);
                    }
                    // Pass IdP groups for group-to-role mapping
                    // Priority: 1) custom:samlGroups (SAML IdPs like Okta), 2) cognito:groups
                    // (Cognito groups)
                    java.util.Set<String> allGroups = new java.util.LinkedHashSet<>();

                    // 1. Read SAML groups from custom:samlGroups (contains actual IdP group names
                    // like "dev", "Admins")
                    String samlGroups = jwt.getClaimAsString("custom:samlGroups");
                    if (samlGroups != null && !samlGroups.isBlank()) {
                        // Cognito stores multi-valued SAML attributes as "[val1, val2]" format
                        // Strip brackets if present
                        String cleaned = samlGroups.trim();
                        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                            cleaned = cleaned.substring(1, cleaned.length() - 1);
                        }
                        // Split by comma and trim each value
                        for (String g : cleaned.split(",")) {
                            if (g != null && !g.isBlank()) {
                                allGroups.add(g.trim());
                            }
                        }
                        log.debug("Found SAML groups in custom:samlGroups: {} -> parsed: {}", samlGroups, allGroups);
                    }

                    // 2. Also read cognito:groups (filter out tenant_ groups)
                    List<String> cognitoGroups = jwt.getClaimAsStringList("cognito:groups");
                    if (cognitoGroups != null) {
                        cognitoGroups.stream()
                                .filter(g -> g != null && !g.startsWith(TENANT_GROUP_PREFIX))
                                .forEach(allGroups::add);
                    }

                    if (!allGroups.isEmpty()) {
                        String groups = String.join(",", allGroups);
                        builder.header("X-Groups", groups);
                        log.debug("Passing IdP groups to downstream: {}", groups);
                    }

                    log.debug("NT-01 allow path={} userId={} tenantId={}",
                            exchange.getRequest().getPath(), userId, tenantId);
                    var mutatedExchange = exchange.mutate().request(builder.build()).build();
                    return chain.filter(mutatedExchange);
                })
                .switchIfEmpty(chain.filter(exchange));
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
