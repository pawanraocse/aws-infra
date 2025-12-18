package com.learning.authservice.authorization.repository;

import com.learning.authservice.authorization.domain.GroupRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GroupRoleMapping entities.
 */
@Repository
public interface GroupRoleMappingRepository extends JpaRepository<GroupRoleMapping, UUID> {

    /**
     * Find all group-role mappings.
     */
    List<GroupRoleMapping> findAllByOrderByPriorityDesc();

    /**
     * Find a mapping by external group ID.
     */
    Optional<GroupRoleMapping> findByExternalGroupId(String externalGroupId);

    /**
     * Find mappings for multiple external group IDs (for batch role resolution).
     * Ordered by priority descending so highest priority comes first.
     */
    @Query("SELECT m FROM GroupRoleMapping m WHERE m.externalGroupId IN :groupIds AND m.autoAssign = true ORDER BY m.priority DESC")
    List<GroupRoleMapping> findAutoAssignMappingsByExternalGroupIds(@Param("groupIds") List<String> groupIds);

    /**
     * Find all mappings with auto-assign enabled.
     */
    List<GroupRoleMapping> findByAutoAssignTrue();

    /**
     * Check if a mapping exists for the given external group ID.
     */
    boolean existsByExternalGroupId(String externalGroupId);

    /**
     * Delete a mapping by external group ID.
     */
    void deleteByExternalGroupId(String externalGroupId);

    /**
     * Find all mappings for a specific role.
     */
    List<GroupRoleMapping> findByRoleId(String roleId);
}
