package com.learning.common.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test implementation of RoleLookupService.
 * Uses in-memory map instead of calling auth-service.
 * Useful for unit tests and local development without auth-service.
 */
@Service
@Profile("test")
@Slf4j
public class TestRoleLookupService implements RoleLookupService {

    private final Map<String, String> userRoles = new ConcurrentHashMap<>();

    /**
     * Default behavior: returns "admin" for any user (for test convenience).
     * Can be overridden with setUserRole().
     */
    @Override
    public Optional<String> getUserRole(String userId, String tenantId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        String role = userRoles.getOrDefault(userId, "admin");
        log.debug("TestRoleLookupService: userId={} tenantId={} role={}", userId, tenantId, role);
        return Optional.of(role);
    }

    /**
     * Set role for a specific user (for testing).
     */
    public void setUserRole(String userId, String role) {
        userRoles.put(userId, role);
    }

    /**
     * Clear all role mappings.
     */
    public void clear() {
        userRoles.clear();
    }
}
