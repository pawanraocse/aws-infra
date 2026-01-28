package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.common.infra.security.RoleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Local implementation of RoleLookupService.
 * Direct database access avoids self-referential network calls (loopback
 * issues).
 * Used within auth-service instead of RemoteRoleLookupService.
 */
@RequiredArgsConstructor
@Slf4j
public class LocalRoleLookupService implements RoleLookupService {

    private final UserRoleService userRoleService;
    private final GroupRoleMappingService groupRoleMappingService;

    @Override
    public Optional<String> getUserRole(String userId, String tenantId) {
        return getUserRole(userId, tenantId, null);
    }

    @Override
    public Optional<String> getUserRole(String userId, String tenantId, String groups) {
        log.debug("Local role lookup for userId={} groups={}", userId, groups);

        // Priority 1: Check IdP group mappings
        if (groups != null && !groups.isBlank()) {
            List<String> groupList = Arrays.asList(groups.split(","));
            Optional<String> mappedRole = groupRoleMappingService.resolveRoleFromGroups(groupList);
            if (mappedRole.isPresent()) {
                log.debug("Role from group mapping: userId={} roleId={}", userId, mappedRole.get());
                return mappedRole;
            }
        }

        // Priority 2: Check user_roles table
        List<UserRole> roles = userRoleService.getUserRoles(userId);
        if (!roles.isEmpty()) {
            String roleId = roles.get(0).getRoleId();
            log.debug("Role lookup result: userId={} roleId={}", userId, roleId);
            return Optional.of(roleId);
        }

        // Priority 3: Default to viewer
        log.debug("No roles found for userId={}, returning default 'viewer' role", userId);
        return Optional.of("viewer");
    }
}
