package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.service.PermissionService;
import com.learning.authservice.authorization.service.UserRoleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.learning.authservice.authorization.domain.Permission;
import java.util.List;
import java.util.Set;

/**
 * Controller for checking permissions.
 * 
 * <p>
 * Role lookup is now done via UserRoleService (database) instead of X-Role
 * header
 * for better security.
 * </p>
 * 
 * <p>
 * Tenant context is implicit via TenantDataSourceRouter.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Slf4j
public class PermissionController {

    private final PermissionService permissionService;
    private final UserRoleService userRoleService;

    /**
     * Check if a user has a specific permission.
     * Super-admin (from database) gets automatic access to all resources.
     */
    @PostMapping("/check")
    public ResponseEntity<Boolean> checkPermission(@RequestBody PermissionCheckRequest request) {
        // Lookup role from database
        List<UserRole> userRoles = userRoleService.getUserRoles(request.getUserId());
        String role = userRoles.isEmpty() ? null : userRoles.get(0).getRoleId();

        // Super-admin bypass: grant all permissions
        if ("super-admin".equals(role)) {
            log.debug("Super-admin permission granted for resource={}:{}",
                    request.getResource(), request.getAction());
            return ResponseEntity.ok(true);
        }

        boolean allowed = permissionService.hasPermission(
                request.getUserId(),
                request.getResource(),
                request.getAction());
        return ResponseEntity.ok(allowed);
    }

    /**
     * Get all defined permissions.
     * 
     * @deprecated Granular permissions have been replaced with role-based access
     *             (admin, editor, viewer).
     *             This endpoint returns an empty list for backward compatibility.
     */
    @Deprecated
    @GetMapping
    public ResponseEntity<List<Permission>> listPermissions() {
        // Granular permissions removed - now using simplified role-based access
        // Return empty list for backward compatibility
        log.debug("listPermissions called - returning empty list (granular permissions deprecated)");
        return ResponseEntity.ok(List.of());
    }

    /**
     * Get all permissions for a user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Set<String>> getUserPermissions(@PathVariable String userId) {
        return ResponseEntity.ok(permissionService.getUserPermissions(userId));
    }

    @Data
    public static class PermissionCheckRequest {
        private String userId;
        private String resource;
        private String action;
    }
}
