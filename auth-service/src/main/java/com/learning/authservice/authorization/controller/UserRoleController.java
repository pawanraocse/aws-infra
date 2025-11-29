package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.common.infra.security.RequirePermission;
import com.learning.common.infra.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing user roles.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Slf4j
public class UserRoleController {

    private final UserRoleService userRoleService;

    @PostMapping("/assign")
    @RequirePermission(resource = "user", action = "manage")
    public ResponseEntity<Void> assignRole(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid RoleAssignmentRequest request) {

        String assignedBy = jwt.getSubject();
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Assigning role {} to user {} in tenant {}", request.getRoleId(), request.getUserId(), tenantId);

        userRoleService.assignRole(request.getUserId(), tenantId, request.getRoleId(), assignedBy);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke")
    @RequirePermission(resource = "user", action = "manage")
    public ResponseEntity<Void> revokeRole(
            @RequestBody @Valid RoleRevocationRequest request) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("Revoking role {} from user {} in tenant {}", request.getRoleId(), request.getUserId(), tenantId);

        userRoleService.revokeRole(request.getUserId(), tenantId, request.getRoleId());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    @RequirePermission(resource = "user", action = "read")
    public ResponseEntity<List<UserRole>> getUserRoles(
            @PathVariable String userId) {

        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(userRoleService.getUserRoles(userId, tenantId));
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
