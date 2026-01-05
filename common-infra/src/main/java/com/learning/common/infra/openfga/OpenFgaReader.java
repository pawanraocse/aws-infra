package com.learning.common.infra.openfga;

/**
 * Interface for reading (checking) permissions from OpenFGA.
 * 
 * SOLID Principle: Interface Segregation (ISP)
 * - Readers only need check() and listObjects()
 * - Writers have a separate interface for writeTuple()/deleteTuple()
 * 
 * Usage:
 * - Backend-service: Injects OpenFgaReader for permission checks
 * - Auth-service: Injects OpenFgaWriter for tuple management
 */
public interface OpenFgaReader {

    /**
     * Check if user has a specific relation to a resource.
     * 
     * @param userId       User identifier
     * @param relation     Relation to check (e.g., "can_view", "can_edit")
     * @param resourceType Type of resource (e.g., "folder", "document")
     * @param resourceId   Resource identifier
     * @return true if allowed, false otherwise
     */
    boolean check(String userId, String relation, String resourceType, String resourceId);

    /**
     * List all resources of a type that user has a relation to.
     * 
     * @param userId       User identifier
     * @param relation     Relation to filter by (e.g., "can_view")
     * @param resourceType Type of resources to list
     * @return List of resource IDs user has access to
     */
    java.util.List<String> listObjects(String userId, String relation, String resourceType);

    /**
     * Check if OpenFGA is enabled.
     */
    boolean isEnabled();
}
