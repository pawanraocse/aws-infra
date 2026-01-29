package com.learning.common.infra.tenant;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener that automatically injects tenant_id on persist/update.
 * 
 * This listener ensures that all TenantAwareEntity instances have their
 * tenant_id set from the current TenantContext before being persisted.
 */
@Component
@Slf4j
public class TenantIdInterceptor {

    /**
     * Called before INSERT - sets tenant_id from TenantContext if not already set.
     */
    @PrePersist
    public void setTenantIdOnCreate(Object entity) {
        if (entity instanceof TenantAwareEntity tenantEntity) {
            if (tenantEntity.getTenantId() == null) {
                String currentTenant = TenantContext.getCurrentTenant();
                if (currentTenant != null) {
                    tenantEntity.setTenantId(currentTenant);
                    log.debug("Auto-set tenant_id={} on entity {}", currentTenant, entity.getClass().getSimpleName());
                } else {
                    log.warn("No tenant in context when persisting {}", entity.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Called before UPDATE - validates tenant_id matches current context.
     */
    @PreUpdate
    public void validateTenantIdOnUpdate(Object entity) {
        if (entity instanceof TenantAwareEntity tenantEntity) {
            String entityTenant = tenantEntity.getTenantId();
            String currentTenant = TenantContext.getCurrentTenant();
            
            if (entityTenant != null && currentTenant != null && !entityTenant.equals(currentTenant)) {
                log.error("Tenant mismatch on update! entity_tenant={} context_tenant={} entity={}",
                        entityTenant, currentTenant, entity.getClass().getSimpleName());
                throw new IllegalStateException(
                        "Cross-tenant update attempted: entity belongs to " + entityTenant 
                        + " but current context is " + currentTenant);
            }
        }
    }
}
