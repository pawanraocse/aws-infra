package com.learning.platformservice.membership.entity;

/**
 * Status of a user's membership in a tenant.
 */
public enum MembershipStatus {
    /**
     * Active membership - user can access this tenant.
     */
    ACTIVE,

    /**
     * Removed membership - user left or was removed from tenant.
     */
    REMOVED,

    /**
     * Suspended membership - temporarily blocked (e.g., for policy violation).
     */
    SUSPENDED
}
