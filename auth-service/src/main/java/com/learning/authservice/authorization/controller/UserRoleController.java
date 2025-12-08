package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.common.infra.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing user roles.
 * Tenant context is implicit via TenantDataSourceRouter.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Slf4j
public class UserRoleController {

    private final UserRoleService userRoleService;

    @GetMapping
    @RequirePermission(resource = "role", action = "read")
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(userRoleService.getAllRoles());
    }

    @PostMapping("/assign")
    @RequirePermission(resource = "user", action = "manage")
    public ResponseEntity<Void> assignRole(
            @RequestHeader("X-User-Id") String assignedBy,
            @RequestBody @Valid RoleAssignmentRequest request) {

        log.info("Assigning role {} to user {}", request.getRoleId(), request.getUserId());

        userRoleService.assignRole(request.getUserId(), request.getRoleId(), assignedBy);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke")
    @RequirePermission(resource = "user", action = "manage")
    public ResponseEntity<Void> revokeRole(
            @RequestBody @Valid RoleRevocationRequest request) {

        log.info("Revoking role {} from user {}", request.getRoleId(), request.getUserId());

        userRoleService.revokeRole(request.getUserId(), request.getRoleId());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    @RequirePermission(resource = "user", action = "read")
    public ResponseEntity<List<UserRole>> getUserRoles(
            @PathVariable String userId) {

        return ResponseEntity.ok(userRoleService.getUserRoles(userId));
    }

    @PutMapping("/users/{userId}")
    @RequirePermission(resource = "user", action = "manage")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String assignedBy,
            @RequestBody @Valid RoleAssignmentRequest request) {

        if (!userId.equals(request.getUserId())) {
            throw new IllegalArgumentException("Path ID and Body ID mismatch");
        }

        userRoleService.updateUserRole(userId, request.getRoleId(), assignedBy);
        return ResponseEntity.ok().build();
    }

    // DTOs
    @Data
    public static class RoleAssignmentRequest {
        @NotBlank(message = "User ID is required")
        private String userId;

        @NotBlank(message = "Role ID is required")
        private String roleId;
    }

    @Data
    public static class RoleRevocationRequest {
        @NotBlank(message = "User ID is required")
        private String userId;

        @NotBlank(message = "Role ID is required")
        private String roleId;
    }
}
