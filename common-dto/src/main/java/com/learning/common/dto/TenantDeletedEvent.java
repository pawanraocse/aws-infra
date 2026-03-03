package com.learning.common.dto;

/**
 * SNS event published when a tenant is soft-deleted.
 * Consumed by cleanup subscribers (DB drop, S3 cleanup, etc.) via SQS fanout.
 */
public record TenantDeletedEvent(
        String tenantId,
        String tenantType,
        String dbUrl,
        String ownerEmail,
        String deletedBy,
        String deletedAt) {
}
