package com.learning.platformservice.tenant.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Placeholder for future Cognito admin seeding. Currently NO-OP adhering to interface.
 */
@Component
@Slf4j
@Order(80)
public class AdminUserSeedAction implements TenantProvisionAction {
    @Override
    public void execute(TenantProvisionContext context) {
        // Future: invoke Cognito AdminCreateUser + group assignment.
        log.debug("admin_seed_noop tenantId={}", context.getTenant().getId());
    }
}

