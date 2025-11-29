package com.learning.common.infra.security;

import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspect to intercept methods annotated with @RequirePermission and enforce
 * access control.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

    private final PermissionEvaluator permissionEvaluator;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        // 1. Get current user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }

        // Assuming the principal name is the userId (sub) or we extract it from details
        // In our Gateway setup, X-User-Id header is passed, but in backend services,
        // we might rely on Spring Security context populated by a filter or JWT
        // decoder.
        // For now, assume authentication.getName() returns the userId.
        String userId = authentication.getName();

        // 2. Get current tenant
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            // Fallback: try to get from authentication details if available, or throw
            // For PBAC, tenant context is usually required.
            // Some platform-level actions might not have a tenant context in ThreadLocal,
            // but for @RequirePermission usage in tenant services, it should be there.
            log.warn("No tenant context found for permission check");
            throw new AccessDeniedException("No tenant context found");
        }

        String resource = requirePermission.resource();
        String action = requirePermission.action();

        log.debug("Checking permission for user={} tenant={} resource={} action={}",
                userId, tenantId, resource, action);

        // 3. Evaluate permission
        boolean allowed = permissionEvaluator.hasPermission(userId, tenantId, resource, action);

        if (!allowed) {
            log.warn("Access denied: user={} tenant={} resource={} action={}",
                    userId, tenantId, resource, action);
            throw new AccessDeniedException(
                    "You do not have permission to " + action + " " + resource);
        }

        return joinPoint.proceed();
    }
}
