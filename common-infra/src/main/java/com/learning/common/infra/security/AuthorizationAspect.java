package com.learning.common.infra.security;

import com.learning.common.infra.exception.PermissionDeniedException;
import com.learning.common.infra.openfga.OpenFgaPermissionEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Aspect to intercept methods annotated with @RequirePermission and enforce
 * access control.
 * 
 * <p>
 * Supports two modes:
 * </p>
 * <ul>
 * <li><b>RBAC (default)</b>: Checks org-level permissions via
 * PermissionEvaluator</li>
 * <li><b>OpenFGA (when resourceIdParam specified)</b>: Additionally checks
 * resource-level
 * permissions via OpenFGA for fine-grained access control</li>
 * </ul>
 * 
 * <p>
 * Role lookup is done via RoleLookupService which queries the database.
 * </p>
 * <p>
 * Tenant isolation is handled via TenantDataSourceRouter.
 * </p>
 */
@Aspect
@Component
@Slf4j
public class AuthorizationAspect {

    private final PermissionEvaluator permissionEvaluator;
    private final RoleLookupService roleLookupService;
    private final PermissionAuditLogger auditLogger;

    // Optional: Only injected when openfga.enabled=true
    @Autowired(required = false)
    private OpenFgaPermissionEvaluator fgaEvaluator;

    private static final String USER_HEADER = "X-User-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    public AuthorizationAspect(PermissionEvaluator permissionEvaluator,
            RoleLookupService roleLookupService,
            PermissionAuditLogger auditLogger) {
        this.permissionEvaluator = permissionEvaluator;
        this.roleLookupService = roleLookupService;
        this.auditLogger = auditLogger;
    }

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        try {
            // 1. Get current request
            HttpServletRequest request = getCurrentHttpRequest();

            // 2. Get current user and tenant from headers
            String userId = request != null ? request.getHeader(USER_HEADER) : null;
            String tenantId = request != null ? request.getHeader(TENANT_HEADER) : null;

            if (userId == null || userId.isBlank()) {
                log.warn("No user ID found in request header: {}", USER_HEADER);
                throw new PermissionDeniedException("User is not authenticated (missing " + USER_HEADER + ")");
            }

            String resource = requirePermission.resource();
            String action = requirePermission.action();
            String resourceIdParam = requirePermission.resourceIdParam();

            // 3. Check for super-admin bypass via database lookup
            if (roleLookupService.isSuperAdmin(userId, tenantId)) {
                log.debug("Super-admin access granted for user={}", userId);
                auditLogger.logOrgLevelCheck(userId, tenantId, resource, action,
                        PermissionAuditLogger.Decision.ALLOWED_SUPER_ADMIN);
                return joinPoint.proceed();
            }

            // 4. If resourceIdParam is specified, try OpenFGA first (if enabled)
            if (!resourceIdParam.isBlank()) {
                String resourceId = extractResourceId(joinPoint, resourceIdParam);
                if (resourceId != null && fgaEvaluator != null) {
                    log.debug("Checking OpenFGA permission: user={} resource={}:{} action={}",
                            userId, resource, resourceId, action);

                    boolean fgaAllowed = fgaEvaluator.hasResourcePermission(userId, resource, resourceId, action);
                    if (fgaAllowed) {
                        auditLogger.logResourceLevelCheck(userId, tenantId, resource, resourceId, action,
                                PermissionAuditLogger.Decision.ALLOWED_FGA);
                        return joinPoint.proceed();
                    }
                    // FGA denied, fall through to RBAC check
                }
            }

            log.debug("Checking RBAC permission for user={} resource={} action={}", userId, resource, action);

            // 5. Evaluate RBAC permission (tenant context is implicit via DB routing)
            boolean allowed = permissionEvaluator.hasPermission(userId, resource, action);

            if (!allowed) {
                log.warn("Access denied: user={} resource={} action={}", userId, resource, action);
                if (resourceIdParam.isBlank()) {
                    auditLogger.logOrgLevelCheck(userId, tenantId, resource, action,
                            PermissionAuditLogger.Decision.DENIED);
                } else {
                    String resourceId = extractResourceId(joinPoint, resourceIdParam);
                    auditLogger.logResourceLevelCheck(userId, tenantId, resource, resourceId, action,
                            PermissionAuditLogger.Decision.DENIED);
                }
                throw new PermissionDeniedException("You do not have permission to " + action + " " + resource);
            }

            auditLogger.logOrgLevelCheck(userId, tenantId, resource, action,
                    PermissionAuditLogger.Decision.ALLOWED_RBAC);
            return joinPoint.proceed();

        } catch (PermissionDeniedException e) {
            throw e; // Expected exception, propagate as-is
        } catch (Throwable e) {
            log.error("CRITICAL ERROR in AuthorizationAspect: {}", e.getMessage(), e);
            throw e; // Propagate to let Spring handle it (500)
        }
    }

    /**
     * Extract resource ID from method parameter by name.
     */
    private String extractResourceId(ProceedingJoinPoint joinPoint, String paramName) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getName().equals(paramName)) {
                    return args[i] != null ? args[i].toString() : null;
                }
            }
            log.warn("Could not find parameter '{}' in method {}", paramName, method.getName());
        } catch (Exception e) {
            log.error("Failed to extract resourceId from parameter '{}': {}", paramName, e.getMessage());
        }
        return null;
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
