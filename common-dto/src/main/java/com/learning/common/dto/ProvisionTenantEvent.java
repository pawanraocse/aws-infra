package com.learning.common.dto;

/**
 * SQS event for async tenant provisioning.
 * Sent by auth-service after creating a tenant row in PROVISIONING status.
 * Consumed by platform-service to execute provisioning actions (DB creation, migrations).
 */
public record ProvisionTenantEvent(
        String tenantId,
        String name,
        String storageMode,
        String slaTier,
        TenantType tenantType,
        String ownerEmail,
        Integer maxUsers) {

    /**
     * Convert from ProvisionTenantRequest.
     */
    public static ProvisionTenantEvent fromRequest(ProvisionTenantRequest request) {
        return new ProvisionTenantEvent(
                request.id(),
                request.name(),
                request.storageMode(),
                request.slaTier(),
                request.tenantType(),
                request.ownerEmail(),
                request.maxUsers());
    }

    /**
     * Convert to ProvisionTenantRequest (used by consumer).
     */
    public ProvisionTenantRequest toRequest() {
        return new ProvisionTenantRequest(
                tenantId,
                name,
                storageMode,
                slaTier,
                tenantType,
                ownerEmail,
                maxUsers);
    }
}
