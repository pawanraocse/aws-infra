package com.learning.platformservice.security;

import com.learning.common.infra.security.PermissionEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;

/**
 * Local implementation of PermissionEvaluator for Platform Service.
 * Trusts the X-Role header from Gateway and uses a simple role-to-permission
 * matrix.
 * Tenant isolation is handled via TenantDataSourceRouter.
 */
@Component
@Slf4j
public class LocalPermissionEvaluator implements PermissionEvaluator {

    /**
     * Role to permissions mapping for platform-level resources.
     * Format: role -> Set of "resource:action" patterns
     * "*" means all resources/actions allowed
     */
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "super-admin", Set.of("*:*"), // Full access to everything
            "tenant-admin", Set.of(
                    "tenant:read", // Can view own tenant
                    "tenant:update", // Can update own tenant
                    "organization:read", // Can view organization
                    "organization:manage" // Can manage organization settings
            ),
            "member", Set.of(
                    "tenant:read" // Can view tenant info only
            ));

    @Override
    public boolean hasPermission(String userId, String resource, String action) {
        String role = extractRoleFromRequest();

        log.debug("Local permission check: user={}, role={}, resource={}, action={}",
                userId, role, resource, action);

        if (role == null || role.isBlank()) {
            log.warn("No X-Role header found, denying access");
            return false;
        }

        Set<String> permissions = ROLE_PERMISSIONS.get(role);
        if (permissions == null) {
            log.warn("Unknown role: {}, denying access", role);
            return false;
        }

        // Check for wildcard access
        if (permissions.contains("*:*")) {
            log.debug("Role {} has wildcard access, granting permission", role);
            return true;
        }

        // Check for resource wildcard (e.g., "tenant:*")
        if (permissions.contains(resource + ":*")) {
            log.debug("Role {} has wildcard for resource {}, granting permission", role, resource);
            return true;
        }

        // Check for exact permission
        String requiredPermission = resource + ":" + action;
        if (permissions.contains(requiredPermission)) {
            log.debug("Role {} has exact permission {}, granting access", role, requiredPermission);
            return true;
        }

        log.debug("Permission denied: role {} does not have {}", role, requiredPermission);
        return false;
    }

    private String extractRoleFromRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getHeader("X-Role");
            }
        } catch (Exception e) {
            log.debug("Could not extract X-Role from request context: {}", e.getMessage());
        }
        return null;
    }
}
