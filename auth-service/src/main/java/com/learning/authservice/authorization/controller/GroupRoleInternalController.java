package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.service.GroupRoleMappingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Internal API for group-based role resolution.
 * Called by Lambda functions during SSO login flow.
 * NOT exposed via gateway public routes.
 * 
 * <p>
 * This controller provides internal endpoints for the PreTokenGeneration Lambda
 * to resolve roles based on IdP group memberships. It uses the existing
 * {@link GroupRoleMappingService} to find matching role mappings.
 * </p>
 * 
 * <h3>Lambda Flow:</h3>
 * <ol>
 * <li>PreTokenGeneration Lambda extracts groups from SSO claims</li>
 * <li>Lambda calls this endpoint to resolve role from groups</li>
 * <li>Highest priority matching role is returned for JIT provisioning</li>
 * </ol>
 */
@RestController
@RequestMapping("/internal/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupRoleInternalController {

    private final GroupRoleMappingService groupRoleMappingService;

    /**
     * Resolve the highest-priority role from a list of IdP groups.
     * 
     * <p>
     * This endpoint is called by the PreTokenGeneration Lambda to determine
     * which role to assign to an SSO user based on their group memberships.
     * The role resolution follows these rules:
     * </p>
     * 
     * <ul>
     * <li>Only groups with auto_assign=true are considered</li>
     * <li>Groups are matched by external_group_id</li>
     * <li>The highest priority mapping wins (higher number = higher priority)</li>
     * <li>If no mappings found, returns null roleId (caller should use
     * default)</li>
     * </ul>
     * 
     * @param request Contains the list of external group IDs from the IdP
     * @return Response containing the resolved roleId and roleName, or null if no
     *         mapping
     */
    @PostMapping("/resolve-role")
    public ResponseEntity<RoleResolutionResponse> resolveRole(
            @Valid @RequestBody RoleResolutionRequest request) {

        log.debug("Resolving role for {} groups",
                request.groups() != null ? request.groups().size() : 0);

        if (request.groups() == null || request.groups().isEmpty()) {
            log.debug("No groups provided, returning empty resolution");
            return ResponseEntity.ok(RoleResolutionResponse.noMatch());
        }

        Set<Role> roles = groupRoleMappingService.resolveRolesForGroups(request.groups());

        if (roles.isEmpty()) {
            log.debug("No role mappings found for groups: {}", request.groups());
            return ResponseEntity.ok(RoleResolutionResponse.noMatch());
        }

        // Return highest priority role (first in set since service returns ordered by
        // priority)
        Role primaryRole = roles.iterator().next();

        log.info("Resolved role '{}' for groups: {}",
                primaryRole.getId(), request.groups());

        return ResponseEntity.ok(RoleResolutionResponse.of(primaryRole));
    }

    // ========== Request/Response DTOs ==========

    /**
     * Request DTO for role resolution.
     * Contains the list of external group IDs from the IdP.
     */
    public record RoleResolutionRequest(
            @NotNull(message = "Groups list is required") List<String> groups) {
    }

    /**
     * Response DTO for role resolution.
     * Contains the resolved role information or null if no mapping found.
     */
    public record RoleResolutionResponse(
            String roleId,
            String roleName,
            String accessLevel,
            boolean matched) {
        /**
         * Create a response for a successful role match.
         */
        public static RoleResolutionResponse of(Role role) {
            return new RoleResolutionResponse(
                    role.getId(),
                    role.getName(),
                    role.getAccessLevel(),
                    true);
        }

        /**
         * Create an empty response when no mapping is found.
         * Caller should use default role (e.g., 'viewer').
         */
        public static RoleResolutionResponse noMatch() {
            return new RoleResolutionResponse(null, null, null, false);
        }
    }
}
