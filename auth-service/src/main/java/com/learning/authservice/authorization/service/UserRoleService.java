package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.repository.RoleRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.learning.common.infra.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing user role assignments.
 * Tenant isolation is handled via TenantDataSourceRouter - all operations
 * automatically run against the current tenant's database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * Assign a role to a user.
     * Tenant context is implicit via TenantDataSourceRouter.
     *
     * @param userId     Cognito user ID
     * @param roleId     Role ID (e.g., "tenant-admin", "tenant-user")
     * @param assignedBy User ID of the admin performing the assignment
     */
    @CacheEvict(value = { CacheNames.USER_PERMISSIONS, CacheNames.USER_ALL_PERMISSIONS }, key = "#userId")
    public void assignRole(String userId, String roleId, String assignedBy) {
        log.info("Assigning role {} to user {} by {}", roleId, userId, assignedBy);

        // 1. Validate role exists
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        // 2. Log scope info
        if (role.getScope() == Role.RoleScope.PLATFORM) {
            log.debug("Assigning platform scope role {}", roleId);
        }

        // 3. Check if assignment already exists
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            log.warn("User {} already has role {}", userId, roleId);
            return;
        }

        // 4. Create assignment
        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .assignedBy(assignedBy)
                .build();

        userRoleRepository.save(userRole);
        log.info("Role assigned successfully");
    }

    /**
     * Revoke a role from a user.
     *
     * @param userId Cognito user ID
     * @param roleId Role ID
     */
    @CacheEvict(value = { "userPermissions", "userAllPermissions" }, key = "#userId")
    public void revokeRole(String userId, String roleId) {
        log.info("Revoking role {} from user {}", roleId, userId);

        if (!userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            log.warn("User {} does not have role {}", userId, roleId);
            return;
        }

        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
        log.info("Role revoked successfully");
    }

    /**
     * Get all active roles for a user.
     *
     * @param userId Cognito user ID
     * @return List of UserRole entities
     */
    @Transactional(readOnly = true)
    public List<UserRole> getUserRoles(String userId) {
        return userRoleRepository.findActiveRolesByUserId(userId, Instant.now());
    }

    /**
     * Update a user's role by revoking existing roles and assigning the new one.
     * Assumes single-role-per-user model for this operation.
     */
    @CacheEvict(value = { "userPermissions", "userAllPermissions" }, key = "#userId")
    public void updateUserRole(String userId, String newRoleId, String assignedBy) {
        log.info("Updating role for user {} to {}", userId, newRoleId);

        // 1. Get existing roles
        List<UserRole> existingRoles = getUserRoles(userId);

        // 2. Revoke all existing roles
        for (UserRole role : existingRoles) {
            // Skip if it's already the target role (idempotency)
            if (role.getRoleId().equals(newRoleId)) {
                continue;
            }
            revokeRole(userId, role.getRoleId());
        }

        // 3. Assign new role (if not already present)
        if (existingRoles.stream().noneMatch(r -> r.getRoleId().equals(newRoleId))) {
            assignRole(userId, newRoleId, assignedBy);
        }
    }

    /**
     * Get all available roles.
     *
     * @return List of all roles
     */
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}
