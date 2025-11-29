package com.learning.authservice.authorization.repository;

import com.learning.authservice.authorization.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for RolePermission entity (many-to-many junction table).
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {

    /**
     * Find all permissions for a given role
     */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.roleId = :roleId")
    List<RolePermission> findByRoleId(@Param("roleId") String roleId);

    /**
     * Find all roles that have a given permission
     */
    @Query("SELECT rp FROM RolePermission rp WHERE rp.permissionId = :permissionId")
    List<RolePermission> findByPermissionId(@Param("permissionId") String permissionId);

    /**
     * Check if a role has a specific permission by resource and action
     */
    @Query("""
            SELECT CASE WHEN COUNT(rp) > 0 THEN true ELSE false END
            FROM RolePermission rp
            JOIN Permission p ON rp.permissionId = p.id
            WHERE rp.roleId = :roleId
            AND p.resource = :resource
            AND p.action = :action
            """)
    boolean existsByRoleIdAndResourceAndAction(
            @Param("roleId") String roleId,
            @Param("resource") String resource,
            @Param("action") String action);

    /**
     * Get all permission IDs for a role
     */
    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.roleId = :roleId")
    List<String> findPermissionIdsByRoleId(@Param("roleId") String roleId);
}
