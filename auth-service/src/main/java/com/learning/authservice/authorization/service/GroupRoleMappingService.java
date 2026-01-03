package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.GroupRoleMapping;
import com.learning.authservice.authorization.domain.Role;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing group-to-role mappings and resolving roles from IdP
 * groups.
 */
public interface GroupRoleMappingService {

    /**
     * Create a new group-to-role mapping.
     *
     * @param externalGroupId External group ID from IdP
     * @param groupName       Human-readable group name
     * @param roleId          Target role ID
     * @param priority        Priority for conflict resolution
     * @param createdBy       ID of admin creating the mapping
     * @return Created mapping
     */
    GroupRoleMapping createMapping(String externalGroupId, String groupName, String roleId,
            int priority, String createdBy);

    /**
     * Update an existing mapping.
     *
     * @param mappingId Mapping ID
     * @param roleId    New role ID
     * @param priority  New priority
     * @return Updated mapping
     */
    GroupRoleMapping updateMapping(UUID mappingId, String roleId, int priority);

    /**
     * Delete a mapping by ID.
     *
     * @param mappingId Mapping ID
     */
    void deleteMapping(UUID mappingId);

    /**
     * Get all mappings.
     *
     * @return List of all mappings ordered by priority
     */
    List<GroupRoleMapping> getAllMappings();

    /**
     * Resolve roles for a user based on their IdP group memberships.
     * Returns the set of roles that should be assigned based on active mappings.
     *
     * @param externalGroupIds List of external group IDs the user belongs to
     * @return Set of roles to assign
     */
    Set<Role> resolveRolesForGroups(List<String> externalGroupIds);

    /**
     * Get a mapping by ID.
     *
     * @param mappingId Mapping ID
     * @return Mapping if found
     */
    GroupRoleMapping getMappingById(UUID mappingId);

    /**
     * Check if a mapping exists for the given external group ID.
     *
     * @param externalGroupId External group ID
     * @return true if mapping exists
     */
    boolean mappingExists(String externalGroupId);

    /**
     * Resolve the highest-priority role for given IdP groups.
     * Used at login time to determine user's effective role.
     *
     * @param externalGroupIds List of external group IDs the user belongs to
     * @return Optional containing role ID if mapping found, empty otherwise
     */
    java.util.Optional<String> resolveRoleFromGroups(List<String> externalGroupIds);
}
