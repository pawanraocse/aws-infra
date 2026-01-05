package com.learning.common.infra.openfga;

/**
 * Interface for writing (managing) tuples in OpenFGA.
 * 
 * SOLID Principle: Interface Segregation (ISP)
 * - Writers manage relationship tuples
 * - Readers have a separate interface for checks
 * 
 * Usage:
 * - Auth-service: Injects OpenFgaWriter for tuple management
 * - Backend-service: Should NOT inject this (read-only access)
 * 
 * Single Responsibility Principle (SRP):
 * - This interface is ONLY for tuple writes, not for checks
 */
public interface OpenFgaWriter {

    /**
     * Write a relationship tuple (grant access).
     * 
     * @param userId       User receiving the relation
     * @param relation     Relation to grant (e.g., "owner", "editor", "viewer")
     * @param resourceType Type of resource
     * @param resourceId   Resource identifier
     */
    void writeTuple(String userId, String relation, String resourceType, String resourceId);

    /**
     * Delete a relationship tuple (revoke access).
     * 
     * @param userId       User losing the relation
     * @param relation     Relation to revoke
     * @param resourceType Type of resource
     * @param resourceId   Resource identifier
     */
    void deleteTuple(String userId, String relation, String resourceType, String resourceId);

    /**
     * Write a parent-child relationship for hierarchy.
     * 
     * @param childType  Child resource type
     * @param childId    Child resource ID
     * @param parentType Parent resource type
     * @param parentId   Parent resource ID
     */
    void writeParentRelation(String childType, String childId, String parentType, String parentId);

    /**
     * Check if OpenFGA is enabled.
     */
    boolean isEnabled();
}
