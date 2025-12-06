package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.service.PermissionService;
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
 * Used by other services (via internal calls) and potentially by frontend.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Slf4j
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * Check if a user has a specific permission.
     * This endpoint is public (authenticated) but checks permissions for the
     * requested user/tenant.
     * In a real system, we might restrict who can check whose permissions.
     * For service-to-service calls, we assume trust or use client credentials.
     */
    @PostMapping("/check")
    public ResponseEntity<Boolean> checkPermission(@RequestBody PermissionCheckRequest request) {
        boolean allowed = permissionService.hasPermission(
                request.getUserId(),
                request.getTenantId(),
                request.getResource(),
                request.getAction());
        return ResponseEntity.ok(allowed);
    }

    /**
     * Get all defined permissions.
     */
    @GetMapping
    public ResponseEntity<List<Permission>> listPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    /**
     * Get all permissions for a user in a tenant.
     */
    @GetMapping("/user/{userId}/tenant/{tenantId}")
    public ResponseEntity<Set<String>> getUserPermissions(
            @PathVariable String userId,
            @PathVariable String tenantId) {

        return ResponseEntity.ok(permissionService.getUserPermissions(userId, tenantId));
    }

    @Data
    public static class PermissionCheckRequest {
        private String userId;
        private String tenantId;
        private String resource;
        private String action;
    }
}
