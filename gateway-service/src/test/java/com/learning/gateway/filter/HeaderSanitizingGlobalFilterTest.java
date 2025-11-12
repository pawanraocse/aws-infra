package com.learning.gateway.filter;

import com.learning.gateway.support.BaseGatewayFilterTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

class HeaderSanitizingGlobalFilterTest extends BaseGatewayFilterTest {

    @Test
    @DisplayName("removes spoofable headers when sanitization enabled")
    void removesSpoofableHeadersWhenEnabled() {
        HeaderSanitizingGlobalFilter filter = new HeaderSanitizingGlobalFilter();
        ReflectionTestUtils.setField(filter, "sanitizeEnabled", true);

        var request = get("/api/resource")
                .header("X-User-Id", "spoofed-user")
                .header("X-Username", "malicious")
                .header("X-Email", "bad@example.com")
                .header("X-Tenant-Id", "bad-tenant")
                .header("X-Authorities", "ROLE_ADMIN")
                .header("X-Auth-Signature", "fake")
                .header("Legitimate-Header", "keep")
                .build();

        var webExchange = exchange(request);
        var chain = chain();

        StepVerifier.create(filter.filter(webExchange, chain))
                .verifyComplete();

        var mutatedRequest = chain.lastRequest();
        Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-User-Id")).isFalse();
        Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Username")).isFalse();
        Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Email")).isFalse();
        Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Tenant-Id")).isFalse();
        Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Authorities")).isFalse();
        Assertions.assertThat(mutatedRequest.getHeaders().containsKey("X-Auth-Signature")).isFalse();
        Assertions.assertThat(mutatedRequest.getHeaders().getFirst("Legitimate-Header")).isEqualTo("keep");
    }

    @Test
    @DisplayName("leaves headers untouched when sanitization disabled")
    void leavesHeadersWhenDisabled() {
        HeaderSanitizingGlobalFilter filter = new HeaderSanitizingGlobalFilter();
        ReflectionTestUtils.setField(filter, "sanitizeEnabled", false);

        var request = get("/api/resource")
                .header("X-User-Id", "spoofed-user")
                .header("X-Tenant-Id", "spoofed-tenant")
                .build();

        var webExchange = exchange(request);
        var chain = chain();

        StepVerifier.create(filter.filter(webExchange, chain))
                .verifyComplete();

        var mutatedRequest = chain.lastRequest();
        Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-User-Id")).isEqualTo("spoofed-user");
        Assertions.assertThat(mutatedRequest.getHeaders().getFirst("X-Tenant-Id")).isEqualTo("spoofed-tenant");
    }
}

