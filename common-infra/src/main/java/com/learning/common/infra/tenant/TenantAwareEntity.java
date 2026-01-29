package com.learning.common.infra.tenant;

/**
 * Interface for entities that are tenant-aware.
 * 
 * All entities in the shared personal database must implement this interface
 * to enable automatic tenant_id injection and filtering.
 */
public interface TenantAwareEntity {
    
    /**
     * Get the tenant identifier for this entity.
     * @return tenant ID
     */
    String getTenantId();
    
    /**
     * Set the tenant identifier for this entity.
     * @param tenantId the tenant ID to set
     */
    void setTenantId(String tenantId);
}
