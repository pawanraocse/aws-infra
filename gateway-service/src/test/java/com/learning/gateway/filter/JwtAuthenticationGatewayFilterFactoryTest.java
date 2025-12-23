package com.learning.gateway.filter;

import com.learning.gateway.support.BaseGatewayFilterTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tests for JwtAuthenticationGatewayFilterFactory.
 * 
 * <p>
 * Note: Role lookup has been removed from gateway - downstream services
 * now lookup roles directly from the database via RoleLookupService.
 * These tests verify headers are enriched WITHOUT X-Role.
 * </p>
 */
class JwtAuthenticationGatewayFilterFactoryTest extends BaseGatewayFilterTest {

        private JwtAuthenticationGatewayFilterFactory factory;

        @BeforeEach
        void setUp() {
                factory = new JwtAuthenticationGatewayFilterFactory();
        }

        @Test
        @DisplayName("enriches headers when tenant resolved from cognito groups")
        void enrichesHeadersFromCognitoGroups() {
                GatewayFilter filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

                var request = get("/api/items").build();
                var webExchange = exchange(request);
                var chain = chain();

                JwtAuthenticationToken authentication = jwtAuthentication(
                                jwt(Map.of(
                                                "sub", "user-123",
                                                "username", "jane.doe",
                                                "email", "jane@example.com",
                                                "cognito:groups", List.of("tenant_acme"))),
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));

                Mono<Void> result = filter.filter(webExchange, chain);

                StepVerifier.create(withAuthentication(result, authentication))
                                .verifyComplete();

                var mutatedRequest = chain.lastRequest();
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-User-Id")).isEqualTo("user-123");
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Username")).isEqualTo("jane.doe");
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Email")).isEqualTo("jane@example.com");
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Tenant-Id")).isEqualTo("acme");
                // NOTE: X-Role is no longer set by gateway - services lookup roles directly
                Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Role")).isFalse();
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Authorities")).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("enriches headers when tenant resolved from custom claim")
        void enrichesHeadersFromCustomClaim() {
                GatewayFilter filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

                var request = get("/api/items").build();
                var webExchange = exchange(request);
                var chain = chain();

                JwtAuthenticationToken authentication = jwtAuthentication(
                                jwt(Map.of(
                                                "sub", "user-456",
                                                "email", "john@example.com",
                                                "custom:tenant_id", "tenant_xyz")),
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

                Mono<Void> result = filter.filter(webExchange, chain);

                StepVerifier.create(withAuthentication(result, authentication))
                                .verifyComplete();

                var mutatedRequest = chain.lastRequest();
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-User-Id")).isEqualTo("user-456");
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Tenant-Id")).isEqualTo("tenant_xyz");
                Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Role")).isFalse();
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Authorities")).isEqualTo("ROLE_ADMIN");
        }

        @Test
        @DisplayName("enriches headers when tenant resolved from custom:tenantId claim (camelCase)")
        void enrichesHeadersFromCustomTenantIdClaim() {
                GatewayFilter filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

                var request = get("/api/items").build();
                var webExchange = exchange(request);
                var chain = chain();

                // Test with custom:tenantId - the format our SignupController uses
                JwtAuthenticationToken authentication = jwtAuthentication(
                                jwt(Map.of(
                                                "sub", "user-789",
                                                "email", "alice@acme.com",
                                                "custom:tenantId", "acme-corp")),
                                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));

                Mono<Void> result = filter.filter(webExchange, chain);

                StepVerifier.create(withAuthentication(result, authentication))
                                .verifyComplete();

                var mutatedRequest = chain.lastRequest();
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-User-Id")).isEqualTo("user-789");
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Email")).isEqualTo("alice@acme.com");
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Tenant-Id")).isEqualTo("acme-corp");
                Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Role")).isFalse();
                Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Authorities"))
                                .isEqualTo("ROLE_TENANT_ADMIN");
        }

        @Test
        @DisplayName("returns forbidden when tenant missing and short-circuits chain")
        void returnsForbiddenWhenTenantMissing() {
                GatewayFilter filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

                var request = get("/api/items")
                                .header("X-Request-Id", "req-1")
                                .build();
                var webExchange = exchange(request);
                var chain = chain();

                JwtAuthenticationToken authentication = jwtAuthentication(
                                jwt(Map.of("sub", "user-789")),
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));

                Mono<Void> result = filter.filter(webExchange, chain);

                StepVerifier.create(withAuthentication(result, authentication))
                                .verifyComplete();

                Assertions.assertThat(webExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                Assertions.assertThat(responseBody(webExchange))
                                .contains("\"code\":\"TENANT_MISSING\"")
                                .contains("\"requestId\":\"req-1\"");
                Boolean alreadyRouted = webExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR);
                Assertions.assertThat(alreadyRouted).isTrue();
        }

        @Test
        @DisplayName("returns bad request when multiple tenant groups detected")
        void returnsBadRequestWhenMultipleTenantGroups() {
                GatewayFilter filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

                var request = get("/api/items")
                                .header("X-Request-Id", "req-2")
                                .build();
                var webExchange = exchange(request);
                var chain = chain();

                JwtAuthenticationToken authentication = jwtAuthentication(
                                jwt(Map.of(
                                                "sub", "user-999",
                                                "cognito:groups", List.of("tenant_acme", "tenant_bravo"))),
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));

                Mono<Void> result = filter.filter(webExchange, chain);

                StepVerifier.create(withAuthentication(result, authentication))
                                .verifyComplete();

                Assertions.assertThat(webExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                Assertions.assertThat(responseBody(webExchange))
                                .contains("\"code\":\"TENANT_CONFLICT\"")
                                .contains("\"requestId\":\"req-2\"");
                Boolean alreadyRouted = webExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR);
                Assertions.assertThat(alreadyRouted).isTrue();
        }

        @Test
        @DisplayName("passes through untouched when security context missing")
        void passesThroughWhenSecurityContextMissing() {
                GatewayFilter filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

                var request = get("/api/items").build();
                var webExchange = exchange(request);
                var chain = chain();

                StepVerifier.create(filter.filter(webExchange, chain))
                                .verifyComplete();

                Assertions.assertThat(chain.lastRequest()).isNotNull();
                Assertions.assertThat(chain.lastRequest().getHeaders().containsKey("X-Tenant-Id")).isFalse();
        }

        private Jwt jwt(Map<String, Object> claims) {
                return new Jwt(
                                "token",
                                Instant.now(),
                                Instant.now().plusSeconds(3600),
                                Map.of("alg", "none"),
                                claims);
        }

        private JwtAuthenticationToken jwtAuthentication(Jwt jwt, List<SimpleGrantedAuthority> authorities) {
                return new JwtAuthenticationToken(jwt, authorities);
        }

        private <T> Mono<T> withAuthentication(Mono<T> publisher, JwtAuthenticationToken authentication) {
                return publisher.contextWrite(
                                ReactiveSecurityContextHolder.withSecurityContext(
                                                Mono.just(new SecurityContextImpl(authentication))));
        }
}
