package com.learning.common.infra.openfga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for managing OpenFGA relationship tuples.
 * Call this service when resources are created, shared, or deleted.
 * 
 * Usage:
 * - When user creates a folder: tupleService.grantOwnership(userId, "folder",
 * folderId)
 * - When user shares a folder: tupleService.grantAccess(userId, "editor",
 * "folder", folderId)
 * - When user removes share: tupleService.revokeAccess(userId, "editor",
 * "folder", folderId)
 * - When resource is deleted: tupleService.deleteAllTuples("folder", folderId)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaTupleService {

    private final OpenFgaClientWrapper fgaClient;

    /**
     * Grant ownership when a resource is created.
     * The creator becomes the owner with full permissions.
     * 
     * @param userId       Creator's user ID
     * @param resourceType Type of resource (folder, document, project)
     * @param resourceId   Resource identifier
     */
    public void grantOwnership(String userId, String resourceType, String resourceId) {
        log.info("Granting ownership: user={} -> owner -> {}:{}", userId, resourceType, resourceId);
        fgaClient.writeTuple(userId, "owner", resourceType, resourceId);
    }

    /**
     * Grant access when a resource is shared.
     * 
     * @param userId       User receiving access
     * @param relation     Access level (viewer, editor, owner)
     * @param resourceType Type of resource
     * @param resourceId   Resource identifier
     */
    public void grantAccess(String userId, String relation, String resourceType, String resourceId) {
        log.info("Granting access: user={} -> {} -> {}:{}", userId, relation, resourceType, resourceId);
        fgaClient.writeTuple(userId, relation, resourceType, resourceId);
    }

    /**
     * Revoke access when sharing is removed.
     * 
     * @param userId       User losing access
     * @param relation     Access level to revoke
     * @param resourceType Type of resource
     * @param resourceId   Resource identifier
     */
    public void revokeAccess(String userId, String relation, String resourceType, String resourceId) {
        log.info("Revoking access: user={} -/-> {} -> {}:{}", userId, relation, resourceType, resourceId);
        fgaClient.deleteTuple(userId, relation, resourceType, resourceId);
    }

    /**
     * Set parent relationship for hierarchical permissions.
     * E.g., folder belongs to project, document belongs to folder.
     * 
     * @param childType  Child resource type (folder, document)
     * @param childId    Child resource ID
     * @param parentType Parent resource type (project, folder)
     * @param parentId   Parent resource ID
     */
    public void setParent(String childType, String childId, String parentType, String parentId) {
        log.info("Setting parent: {}:{} -> parent -> {}:{}", childType, childId, parentType, parentId);
        // OpenFGA format: object:childId -> parentRelation -> parentType:parentId
        // This is a special tuple where the "user" is actually another object
        // Example: folder:folder-123 has parent project:proj-456
        // Written as: project:proj-456 -> project -> folder:folder-123
        fgaClient.writeTuple(parentType + ":" + parentId, parentType.toLowerCase(), childType, childId);
    }

    /**
     * Add user to organization.
     * 
     * @param userId         User ID
     * @param organizationId Organization ID
     * @param role           Role (admin, member)
     */
    public void addUserToOrganization(String userId, String organizationId, String role) {
        log.info("Adding user to org: user={} -> {} -> organization:{}", userId, role, organizationId);
        fgaClient.writeTuple(userId, role, "organization", organizationId);
    }

    /**
     * Remove user from organization.
     * 
     * @param userId         User ID
     * @param organizationId Organization ID
     * @param role           Role to remove
     */
    public void removeUserFromOrganization(String userId, String organizationId, String role) {
        log.info("Removing user from org: user={} -/-> {} -> organization:{}", userId, role, organizationId);
        fgaClient.deleteTuple(userId, role, "organization", organizationId);
    }

    /**
     * Link project to organization.
     * Enables organization-level permission inheritance.
     * 
     * @param projectId      Project ID
     * @param organizationId Organization ID
     */
    public void linkProjectToOrganization(String projectId, String organizationId) {
        log.info("Linking project to org: organization:{} -> organization -> project:{}",
                organizationId, projectId);
        fgaClient.writeTuple("organization:" + organizationId, "organization", "project", projectId);
    }
}
