package com.learning.common.infra.security;

import com.learning.common.infra.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.security.access.AccessDeniedException;

/**
 * Aspect to intercept methods annotated with @RequirePermission and enforce
 * access control.
 * Relies on Gateway to inject trusted X-User-Id and X-Tenant-Id headers.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

    private final PermissionEvaluator permissionEvaluator;
    private static final String USER_HEADER = "X-User-Id";

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        // 1. Get current request
        HttpServletRequest request = getCurrentHttpRequest();

        // 2. Get current user from header
        String userId = request != null ? request.getHeader(USER_HEADER) : null;

        if (userId == null || userId.isBlank()) {
            log.warn("No user ID found in request header: {}", USER_HEADER);
            throw new AccessDeniedException("User is not authenticated (missing " + USER_HEADER + ")");
        }

        // 3. Get current tenant
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            log.warn("No tenant context found for permission check");
            throw new AccessDeniedException("No tenant context found");
        }

        String resource = requirePermission.resource();
        String action = requirePermission.action();

        log.debug("Checking permission for user={} tenant={} resource={} action={}",
                userId, tenantId, resource, action);

        // 4. Evaluate permission
        boolean allowed = permissionEvaluator.hasPermission(userId, tenantId, resource, action);

        if (!allowed) {
            log.warn("Access denied: user={} tenant={} resource={} action={}",
                    userId, tenantId, resource, action);
            throw new AccessDeniedException(
                    "You do not have permission to " + action + " " + resource);
        }

        return joinPoint.proceed();
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
