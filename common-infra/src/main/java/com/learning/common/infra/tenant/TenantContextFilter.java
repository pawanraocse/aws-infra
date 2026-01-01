package com.learning.common.infra.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract Tenant ID from request headers and populate TenantContext.
 * Relies on Gateway to inject trusted X-Tenant-Id header.
 * In non-dev profiles, BLOCKS requests without tenant context (security
 * hardening).
 * Ensures context is cleared after request processing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    // Paths that don't require tenant context (public endpoints, internal, health
    // checks)
    private static final String[] EXCLUDED_PATHS = {
            "/actuator", "/swagger", "/v3/api-docs", "/internal",
            // Public auth endpoints (no tenant context known yet)
            "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/verify",
            "/api/v1/auth/sso-complete", // SSO signup completion (tenant creation)
            "/api/v1/auth/lookup", "/api/v1/auth/last-accessed", "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
            "/api/v1/invitations/validate", "/api/v1/invitations/accept",
            // SSO lookup (public - needed for SSO login before tenant context is known)
            "/sso/lookup",
            // Internal service-to-service permission checks (tenant in request body)
            "/api/v1/permissions",
            // Stripe webhooks (verified via signature, not tenant header)
            "/billing/webhook"
    };

    private final Environment environment;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("TenantContextFilter: Setting tenant context: {}", tenantId);
            TenantContext.setCurrentTenant(tenantId);
        } else {
            // Block requests without tenant context in production
            if (!isExcludedPath(request) && !isDevProfile()) {
                log.warn("TenantContextFilter: Blocking request - missing X-Tenant-Id header for path: {}",
                        request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing tenant context");
                return;
            }
            log.debug("TenantContextFilter: No tenant ID (allowed for {} or dev profile)", request.getRequestURI());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExcludedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        // Use contains to handle service-prefixed paths like /auth/actuator/health
        for (String path : EXCLUDED_PATHS) {
            if (uri.contains(path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDevProfile() {
        return environment.acceptsProfiles(Profiles.of("dev", "local", "test"));
    }
}
