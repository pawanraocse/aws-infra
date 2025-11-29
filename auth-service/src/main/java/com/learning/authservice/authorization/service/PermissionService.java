package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.repository.RolePermissionRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for checking user permissions in the authorization system.
 * Implements Permission-Based Access Control (PBAC).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PermissionService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Check if user has permission for a specific resource and action.
     *
     * @param userId   Cognito user ID (sub claim from JWT)
     * @param tenantId Tenant ID
     * @param resource Resource name (e.g., "entry", "user", "tenant")
     * @param action   Action name (e.g., "read", "create", "update", "delete",
     *                 "manage")
     * @return true if user has the permission, false otherwise
     */
    @Cacheable(value = "userPermissions", key = "#userId + ':' + #tenantId + ':' + #resource + ':' + #action")
    public boolean hasPermission(String userId, String tenantId, String resource, String action) {
        log.debug("Checking permission: user={}, tenant={}, resource={}, action={}",
                userId, tenantId, resource, action);

        // 1. Get user's active (non-expired) roles in this tenant
        List<UserRole> userRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(
                userId, tenantId, Instant.now());

        if (userRoles.isEmpty()) {
            log.debug("User {} has no active roles in tenant {}", userId, tenantId);
            return false;
        }

        // 2. Check if any of the user's roles grant the required permission
        for (UserRole userRole : userRoles) {
            // Check for super-admin (platform-level wildcard access)
            if ("super-admin".equals(userRole.getRoleId())) {
                log.debug("User {} is super-admin, granting access", userId);
                return true;
            }

            // Check if role has the specific permission
            boolean roleHasPermission = rolePermissionRepository
                    .existsByRoleIdAndResourceAndAction(
                            userRole.getRoleId(),
                            resource,
                            action);

            if (roleHasPermission) {
                log.debug("Permission granted: user={}, role={}, resource={}:{}",
                        userId, userRole.getRoleId(), resource, action);
                return true;
            }
        }

        log.debug("Permission denied: user={}, tenant={}, resource={}:{}",
                userId, tenantId, resource, action);
        return false;
    }

    /**
     * Get all permissions for a user in a tenant.
     * Returns permissions in "resource:action" format.
     *
     * @param userId   Cognito user ID
     * @param tenantId Tenant ID
     * @return Set of permission strings (e.g., {"entry:read", "entry:create"})
     */
    @Cacheable(value = "userAllPermissions", key = "#userId + ':' + #tenantId")
    public Set<String> getUserPermissions(String userId, String tenantId) {
        log.debug("Getting all permissions for user={} in tenant={}", userId, tenantId);

        // Get user's active roles
        List<UserRole> userRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(
                userId, tenantId, Instant.now());

        if (userRoles.isEmpty()) {
            log.debug("User {} has no active roles in tenant {}", userId, tenantId);
            return Set.of();
        }

        // For super-admin, return wildcard (or could return all permissions)
        boolean isSuperAdmin = userRoles.stream()
                .anyMatch(ur -> "super-admin".equals(ur.getRoleId()));

        if (isSuperAdmin) {
            log.debug("User {} is super-admin", userId);
            return Set.of("*:*"); // Wildcard permission
        }

        // Collect all permissions from all roles
        Set<String> permissions = userRoles.stream()
                .flatMap(userRole -> {
                    List<String> permissionIds = rolePermissionRepository
                            .findPermissionIdsByRoleId(userRole.getRoleId());
                    return permissionIds.stream();
                })
                .collect(Collectors.toSet());

        log.debug("User {} in tenant {} has {} permissions", userId, tenantId, permissions.size());
        return permissions;
    }

    /**
     * Check if user has ANY of the specified permissions.
     *
     * @param userId      User ID
     * @param tenantId    Tenant ID
     * @param permissions List of permission strings in "resource:action" format
     * @return true if user has at least one of the permissions
     */
    public boolean hasAnyPermission(String userId, String tenantId, List<String> permissions) {
        for (String permission : permissions) {
            String[] parts = permission.split(":");
            if (parts.length == 2 && hasPermission(userId, tenantId, parts[0], parts[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has ALL of the specified permissions.
     *
     * @param userId      User ID
     * @param tenantId    Tenant ID
     * @param permissions List of permission strings in "resource:action" format
     * @return true if user has all of the permissions
     */
    public boolean hasAllPermissions(String userId, String tenantId, List<String> permissions) {
        for (String permission : permissions) {
            String[] parts = permission.split(":");
            if (parts.length != 2 || !hasPermission(userId, tenantId, parts[0], parts[1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if user is a tenant admin.
     *
     * @param userId   User ID
     * @param tenantId Tenant ID
     * @return true if user has tenant-admin role
     */
    public boolean isTenantAdmin(String userId, String tenantId) {
        return userRoleRepository.existsByUserIdAndTenantIdAndRoleId(
                userId, tenantId, "tenant-admin");
    }

    /**
     * Check if user is a super admin (platform-level).
     *
     * @param userId User ID
     * @return true if user has super-admin role
     */
    public boolean isSuperAdmin(String userId) {
        List<UserRole> superAdminRoles = userRoleRepository.findByUserId(userId).stream()
                .filter(ur -> "super-admin".equals(ur.getRoleId()))
                .toList();

        return !superAdminRoles.isEmpty();
    }
}
