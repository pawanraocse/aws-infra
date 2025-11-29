package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.repository.RoleRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing user role assignments.
 * Handles assigning and revoking roles for users in tenants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * Assign a role to a user in a specific tenant.
     *
     * @param userId     Cognito user ID
     * @param tenantId   Tenant ID
     * @param roleId     Role ID (e.g., "tenant-admin", "tenant-user")
     * @param assignedBy User ID of the admin performing the assignment
     */
    @CacheEvict(value = { "userPermissions", "userAllPermissions" }, key = "#userId + ':' + #tenantId")
    public void assignRole(String userId, String tenantId, String roleId, String assignedBy) {
        log.info("Assigning role {} to user {} in tenant {} by {}", roleId, userId, tenantId, assignedBy);

        // 1. Validate role exists
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        // 2. Validate scope
        if (role.getScope() == Role.RoleScope.PLATFORM && !"super-admin".equals(roleId)) {
            // Only specific platform roles might be assignable via this API
            // For now, we allow it but log a warning if it's a platform role assigned in a
            // tenant context
            // (Though our schema enforces tenant_id in user_roles, so platform roles are
            // effectively scoped to the tenant in this table
            // unless we treat tenant_id='system' for platform roles. For simplicity, we
            // assume all user_roles are tenant-scoped for now,
            // except maybe super-admin which might apply across all if logic permits, but
            // usually super-admin is a specific user attribute or a role in a 'system'
            // tenant).
            log.debug("Assigning platform scope role {} within tenant {}", roleId, tenantId);
        }

        // 3. Check if assignment already exists
        if (userRoleRepository.existsByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId)) {
            log.warn("User {} already has role {} in tenant {}", userId, roleId, tenantId);
            return;
        }

        // 4. Create assignment
        UserRole userRole = UserRole.builder()
                .userId(userId)
                .tenantId(tenantId)
                .roleId(roleId)
                .assignedBy(assignedBy)
                .build();

        userRoleRepository.save(userRole);
        log.info("Role assigned successfully");
    }

    /**
     * Revoke a role from a user in a specific tenant.
     *
     * @param userId   Cognito user ID
     * @param tenantId Tenant ID
     * @param roleId   Role ID
     */
    @CacheEvict(value = { "userPermissions", "userAllPermissions" }, key = "#userId + ':' + #tenantId")
    public void revokeRole(String userId, String tenantId, String roleId) {
        log.info("Revoking role {} from user {} in tenant {}", roleId, userId, tenantId);

        if (!userRoleRepository.existsByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId)) {
            log.warn("User {} does not have role {} in tenant {}", userId, roleId, tenantId);
            return;
        }

        userRoleRepository.deleteByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId);
        log.info("Role revoked successfully");
    }

    /**
     * Get all active roles for a user in a tenant.
     *
     * @param userId   Cognito user ID
     * @param tenantId Tenant ID
     * @return List of UserRole entities
     */
    @Transactional(readOnly = true)
    public List<UserRole> getUserRoles(String userId, String tenantId) {
        return userRoleRepository.findActiveRolesByUserIdAndTenantId(userId, tenantId, Instant.now());
    }
}
