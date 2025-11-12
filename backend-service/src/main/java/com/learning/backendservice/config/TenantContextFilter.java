package com.learning.backendservice.config;

import com.learning.backendservice.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CODE_TENANT_MISSING = "TENANT_MISSING";
    private static final String CODE_TENANT_INVALID_FORMAT = "TENANT_INVALID_FORMAT";
    private static final Pattern TENANT_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    @Value("${security.backend.allow-missing-tenant:false}")
    private boolean allowMissingTenant;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // NT-24: Exempt actuator endpoints (health/info/etc.) from tenant enforcement
        boolean actuatorExempt = path.startsWith("/actuator/");

        String tenantId = trimOrNull(request.getHeader(TENANT_HEADER));
        String userId = trimOrNull(request.getHeader(USER_ID_HEADER));
        String requestId = headerOrGenerate(request, REQUEST_ID_HEADER);

        try {
            if (!actuatorExempt) {
                if (tenantId == null) {
                    if (!allowMissingTenant) {
                        log.debug("NT-11 deny path={} code={} requestId={}", path, CODE_TENANT_MISSING, requestId);
                        writeError(response, path, requestId, 403, CODE_TENANT_MISSING, "Tenant header required");
                        return;
                    }
                    log.info("NT-11 allow-missing-tenant=true path={} requestId={}", path, requestId);
                } else if (!TENANT_PATTERN.matcher(tenantId).matches()) {
                    String safeTenant = sanitize(tenantId);
                    log.debug("NT-11 deny path={} code={} tenantId={} requestId={}", path, CODE_TENANT_INVALID_FORMAT, safeTenant, requestId);
                    writeError(response, path, requestId, 400, CODE_TENANT_INVALID_FORMAT, "Tenant ID format invalid");
                    return;
                } else {
                    TenantContext.setTenantId(tenantId);
                    if (userId != null) {
                        TenantContext.setUserId(userId);
                    } else {
                        log.debug("No X-User-Id header found, continuing without user context. path={} requestId={}", path, requestId);
                    }
                    log.debug("NT-11 tenant accepted tenantId={} userId={} path={} requestId={}", tenantId, userId, path, requestId);
                }
            } else {
                log.trace("NT-24 actuator exemption path={} requestId={}", path, requestId);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String trimOrNull(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private String headerOrGenerate(HttpServletRequest request, String name) {
        String h = trimOrNull(request.getHeader(name));
        return (h != null) ? h : UUID.randomUUID().toString();
    }

    private void writeError(HttpServletResponse response,
                            String path,
                            String requestId,
                            int status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\",\"path\":\"%s\"}",
                Instant.now(), status, code, escape(message), requestId, path
        );
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String v) {
        return v == null ? "" : v.replace("\"", " ");
    }

    private String sanitize(String tenantId) {
        if (tenantId == null) return "";
        String clean = tenantId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return clean.length() > 80 ? clean.substring(0, 80) + "..." : clean;
    }
}
