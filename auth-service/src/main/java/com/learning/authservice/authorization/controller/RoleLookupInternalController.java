package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.service.GroupRoleMappingService;
import com.learning.authservice.authorization.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final GroupRoleMappingService groupRoleMappingService;

    /**
     * Get the primary role for a user.
     * Priority: IdP group mappings > user_roles > default 'viewer'
     * Called by gateway-service after JWT validation.
     *
     * @param userId Cognito user ID (from JWT sub claim)
     * @param groups Comma-separated IdP groups from X-Groups header
     * @return JSON with roleId field
     */
    @GetMapping("/{userId}/role")
    public ResponseEntity<Map<String, String>> getUserRole(
            @PathVariable String userId,
            @RequestHeader(value = "X-Groups", required = false) String groups) {
        log.debug("Role lookup for userId={} groups={}", userId, groups);

        // Priority 1: Check IdP group mappings
        if (groups != null && !groups.isBlank()) {
            List<String> groupList = Arrays.asList(groups.split(","));
            Optional<String> mappedRole = groupRoleMappingService.resolveRoleFromGroups(groupList);
            if (mappedRole.isPresent()) {
                log.info("Role from group mapping: userId={} groups={} roleId={}", userId, groups, mappedRole.get());
                return ResponseEntity.ok(Map.of("roleId", mappedRole.get()));
            }
        }

        // Priority 2: Check user_roles table
        List<UserRole> roles = userRoleService.getUserRoles(userId);
        if (!roles.isEmpty()) {
            String roleId = roles.get(0).getRoleId();
            log.debug("Role lookup result: userId={} roleId={}", userId, roleId);
            return ResponseEntity.ok(Map.of("roleId", roleId));
        }

        // Priority 3: Default to viewer (SSO users with no mappings)
        log.info("No roles found for userId={}, returning default 'viewer' role", userId);
        return ResponseEntity.ok(Map.of("roleId", "viewer"));
    }
}
