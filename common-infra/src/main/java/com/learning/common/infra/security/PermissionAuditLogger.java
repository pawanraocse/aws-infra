package com.learning.common.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs all permission check decisions for audit and compliance purposes.
 * 
 * <p>
 * Logs include:
 * <ul>
 * <li>userId, tenantId - Who made the request</li>
 * <li>resource, action - What was requested</li>
 * <li>resourceId - Specific resource instance (if applicable)</li>
 * <li>decision - ALLOWED_RBAC, ALLOWED_FGA, DENIED</li>
 * </ul>
 */
@Component
@Slf4j
public class PermissionAuditLogger {

    public enum Decision {
        ALLOWED_SUPER_ADMIN,
        ALLOWED_RBAC,
        ALLOWED_FGA,
        DENIED
    }

    /**
     * Log an org-level permission check (RBAC only).
     */
    public void logOrgLevelCheck(String userId, String tenantId, String resource, String action, Decision decision) {
        log.info("PERMISSION_AUDIT: user={} tenant={} resource={} action={} decision={}",
                userId, tenantId, resource, action, decision);
    }

    /**
     * Log a resource-level permission check (includes resourceId).
     */
    public void logResourceLevelCheck(String userId, String tenantId, String resource, String resourceId,
            String action, Decision decision) {
        log.info("PERMISSION_AUDIT: user={} tenant={} resource={}:{} action={} decision={}",
                userId, tenantId, resource, resourceId, action, decision);
    }
}
