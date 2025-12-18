package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Internal API for role lookup.
 * Called by gateway-service to populate X-Role header.
 * NOT exposed via gateway public routes.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class RoleLookupInternalController {

    private final UserRoleService userRoleService;

    /**
     * Get the primary role for a user.
     * Returns the highest-priority role (admin > editor > viewer > guest).
     * Called by gateway-service after JWT validation.
     *
     * @param userId Cognito user ID (from JWT sub claim)
     * @return JSON with roleId field
     */
    @GetMapping("/{userId}/role")
    public ResponseEntity<Map<String, String>> getUserRole(@PathVariable String userId) {
        log.debug("Role lookup for userId={}", userId);

        List<UserRole> roles = userRoleService.getUserRoles(userId);

        if (roles.isEmpty()) {
            log.warn("No roles found for userId={}", userId);
            return ResponseEntity.ok(Map.of("roleId", ""));
        }

        // Return first role (primary role)
        // In our simplified model, users typically have one role
        String roleId = roles.get(0).getRoleId();
        log.debug("Role lookup result: userId={} roleId={}", userId, roleId);

        return ResponseEntity.ok(Map.of("roleId", roleId));
    }
}
