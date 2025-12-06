package com.learning.platformservice.security;

import com.learning.common.infra.security.PermissionEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Remote implementation of PermissionEvaluator for Platform Service.
 * Calls Auth Service to check permissions.
 * Forwards X-Role header to enable super-admin bypass in auth-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemotePermissionEvaluator implements PermissionEvaluator {

    @Qualifier("authWebClient")
    private final WebClient authWebClient;

    @Override
    @Cacheable(value = "remotePermissions", key = "#userId + ':' + #tenantId + ':' + #resource + ':' + #action", unless = "#result == false")
    public boolean hasPermission(String userId, String tenantId, String resource, String action) {
        log.debug("Checking remote permission: user={}, tenant={}, resource={}, action={}",
                userId, tenantId, resource, action);

        // Get X-Role header from current request context
        String role = extractRoleFromRequest();

        // Super-admin bypass: grant all permissions locally (optimization)
        if ("super-admin".equals(role)) {
            log.debug("Super-admin permission granted locally for resource={}:{}", resource, action);
            return true;
        }

        try {
            Boolean allowed = authWebClient.post()
                    .uri("/api/v1/permissions/check")
                    .header("X-Role", role != null ? role : "")
                    .bodyValue(new PermissionCheckRequest(userId, tenantId, resource, action))
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block(); // Blocking here because PermissionEvaluator is synchronous (used in Aspect)

            return Boolean.TRUE.equals(allowed);
        } catch (Exception e) {
            log.error("Error checking permission with auth-service", e);
            // Fail closed
            return false;
        }
    }

    private String extractRoleFromRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getHeader("X-Role");
            }
        } catch (Exception e) {
            log.debug("Could not extract X-Role from request context: {}", e.getMessage());
        }
        return null;
    }

    @Data
    @RequiredArgsConstructor
    private static class PermissionCheckRequest {
        private final String userId;
        private final String tenantId;
        private final String resource;
        private final String action;
    }
}
