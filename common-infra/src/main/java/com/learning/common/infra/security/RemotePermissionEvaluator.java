package com.learning.common.infra.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Remote implementation of PermissionEvaluator that calls auth-service.
 * Uses the auth-service /api/v1/permissions/check endpoint which queries
 * the database for actual permissions.
 * 
 * Caches permission check results for performance (configured in
 * CacheConfiguration).
 * 
 * Note: Super-admin bypass is handled in AuthorizationAspect before this is
 * called.
 */
@Slf4j
@RequiredArgsConstructor
public class RemotePermissionEvaluator implements PermissionEvaluator {

    private final WebClient authWebClient;

    @Override
    @Cacheable(value = "permissions", key = "#userId + ':' + #resource + ':' + #action")
    public boolean hasPermission(String userId, String resource, String action) {
        log.debug("Checking permission via auth-service: user={}, resource={}, action={}",
                userId, resource, action);

        try {
            Boolean allowed = authWebClient.post()
                    .uri("/api/v1/permissions/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "userId", userId,
                            "resource", resource,
                            "action", action))
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            boolean result = Boolean.TRUE.equals(allowed);
            log.debug("Permission check result: user={}, resource={}, action={}, allowed={}",
                    userId, resource, action, result);
            return result;

        } catch (Exception e) {
            log.error("Permission check failed: user={}, resource={}, action={}, error={}",
                    userId, resource, action, e.getMessage());
            // Fail closed: deny access on error
            return false;
        }
    }
}
