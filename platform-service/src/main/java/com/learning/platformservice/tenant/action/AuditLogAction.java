package com.learning.platformservice.tenant.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Emits audit log after successful provisioning (does not execute on failure path automatically).
 */
@Component
@Slf4j
@Order(90)
public class AuditLogAction implements TenantProvisionAction {
    @Override
    public void execute(TenantProvisionContext context) {
        log.info("tenant_audit event=PROVISION_ATTEMPT tenantId={} storageMode={} sla={} jdbcUrl={}",
                context.getTenant().getId(), context.getRequest().storageMode(), context.getRequest().slaTier(), context.getJdbcUrl());
    }
}

