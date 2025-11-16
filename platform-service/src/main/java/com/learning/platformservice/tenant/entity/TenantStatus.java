package com.learning.platformservice.tenant.entity;

/**
 * Canonical tenant lifecycle statuses.
 */
public enum TenantStatus {
    PROVISIONING,
    MIGRATING,
    ACTIVE,
    PROVISION_ERROR,
    MIGRATION_ERROR;
}

