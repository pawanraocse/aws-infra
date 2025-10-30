package com.learning.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

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

                    // Extract user information from JWT
                    String tenantId = extractTenantId(jwt);
                    String userId = jwt.getSubject();
                    String username = jwt.getClaimAsString("username");
                    String email = jwt.getClaimAsString("email");

                    // Extract authorities
                    String authorities = authentication.getAuthorities().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","));

                    // Add headers for downstream services
                    var request = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-Username", username != null ? username : "")
                            .header("X-Email", email != null ? email : "")
                            .header("X-Tenant-Id", tenantId)
                            .header("X-Authorities", authorities)
                            .build();

                    log.debug("Forwarding request to {} with user: {}, tenant: {}",
                            exchange.getRequest().getPath(), userId, tenantId);

                    return chain.filter(exchange.mutate().request(request).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private String extractTenantId(Jwt jwt) {
        // Extract from cognito:groups (format: tenant_<tenantId>)
        var groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            String tenant = groups.stream()
                    .filter(g -> g.startsWith("tenant_"))
                    .map(g -> g.substring(7))
                    .findFirst()
                    .orElse(null);

            if (tenant != null) {
                return tenant;
            }
        }

        // Or from custom claim
        String tenantClaim = jwt.getClaimAsString("custom:tenant_id");
        if (tenantClaim != null) {
            return tenantClaim;
        }

        log.warn("No tenant ID found in JWT for user: {}", jwt.getSubject());
        return "default";
    }

    public static class Config {
        // Configuration properties if needed in future
    }
}
