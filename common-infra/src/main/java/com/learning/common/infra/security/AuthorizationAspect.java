package com.learning.common.infra.security;

import com.learning.common.infra.exception.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect to intercept methods annotated with @RequirePermission and enforce
 * access control.
 * Relies on Gateway to inject trusted X-User-Id header.
 * Tenant isolation is handled via TenantDataSourceRouter.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorizationAspect {

    private final PermissionEvaluator permissionEvaluator;
    private static final String USER_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-Role";

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        // 1. Get current request
        HttpServletRequest request = getCurrentHttpRequest();

        // 2. Get current user from header
        String userId = request != null ? request.getHeader(USER_HEADER) : null;

        if (userId == null || userId.isBlank()) {
            log.warn("No user ID found in request header: {}", USER_HEADER);
            throw new PermissionDeniedException("User is not authenticated (missing " + USER_HEADER + ")");
        }

        // 3. Check for super-admin bypass
        String role = request.getHeader(ROLE_HEADER);
        if ("super-admin".equals(role)) {
            log.debug("Super-admin access granted for user={}", userId);
            return joinPoint.proceed();
        }

        String resource = requirePermission.resource();
        String action = requirePermission.action();

        log.debug("Checking permission for user={} resource={} action={}", userId, resource, action);

        // 4. Evaluate permission (tenant context is implicit via DB routing)
        boolean allowed = permissionEvaluator.hasPermission(userId, resource, action);

        if (!allowed) {
            log.warn("Access denied: user={} resource={} action={}", userId, resource, action);
            throw new PermissionDeniedException("You do not have permission to " + action + " " + resource);
        }

        return joinPoint.proceed();
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
