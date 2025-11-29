package com.learning.backendservice.security;

import com.learning.common.infra.security.PermissionEvaluator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Remote implementation of PermissionEvaluator for Backend Service.
 * Calls Auth Service to check permissions.
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

        try {
            Boolean allowed = authWebClient.post()
                    .uri("/api/v1/permissions/check")
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

    @Data
    @RequiredArgsConstructor
    private static class PermissionCheckRequest {
        private final String userId;
        private final String tenantId;
        private final String resource;
        private final String action;
    }
}
