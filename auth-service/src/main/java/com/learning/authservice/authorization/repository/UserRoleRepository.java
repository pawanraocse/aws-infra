package com.learning.authservice.authorization.repository;

import com.learning.authservice.authorization.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserRole entity.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Find all roles for a user in a specific tenant
     */
    List<UserRole> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * Find all roles for a user across all tenants
     */
    List<UserRole> findByUserId(String userId);

    /**
     * Find all users with a specific role in a tenant
     */
    List<UserRole> findByTenantIdAndRoleId(String tenantId, String roleId);

    /**
     * Find a specific user role assignment
     */
    Optional<UserRole> findByUserIdAndTenantIdAndRoleId(String userId, String tenantId, String roleId);

    /**
     * Check if user has a specific role in a tenant
     */
    boolean existsByUserIdAndTenantIdAndRoleId(String userId, String tenantId, String roleId);

    /**
     * Find all active (non-expired) roles for a user in a tenant
     */
    @Query("""
            SELECT ur FROM UserRole ur
            WHERE ur.userId = :userId
            AND ur.tenantId = :tenantId
            AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
            """)
    List<UserRole> findActiveRolesByUserIdAndTenantId(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("now") Instant now);

    /**
     * Delete all role assignments for a user in a tenant
     */
    void deleteByUserIdAndTenantId(String userId, String tenantId);

    /**
     * Delete a specific role assignment
     */
    void deleteByUserIdAndTenantIdAndRoleId(String userId, String tenantId, String roleId);
}
