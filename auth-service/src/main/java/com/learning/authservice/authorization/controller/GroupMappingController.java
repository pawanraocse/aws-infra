package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.domain.GroupRoleMapping;
import com.learning.authservice.authorization.service.GroupRoleMappingService;
import com.learning.common.infra.security.RequirePermission;
import com.learning.common.infra.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing group-to-role mappings.
 * Allows tenant admins to configure how IdP groups map to application roles.
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupMappingController {

        private final GroupRoleMappingService mappingService;

        /**
         * Get all group-role mappings for the current tenant.
         */
        @GetMapping("/mappings")
        @RequirePermission(resource = "group", action = "read")
        public ResponseEntity<List<GroupMappingResponse>> getAllMappings() {
                log.debug("Getting all group-role mappings");
                List<GroupRoleMapping> mappings = mappingService.getAllMappings();
                List<GroupMappingResponse> response = mappings.stream()
                                .map(GroupMappingResponse::from)
                                .toList();
                return ResponseEntity.ok(response);
        }

        /**
         * Create a new group-role mapping.
         */
        @PostMapping("/mappings")
        @RequirePermission(resource = "group", action = "manage")
        public ResponseEntity<GroupMappingResponse> createMapping(
                        @Valid @RequestBody CreateMappingRequest request) {
                log.info("Creating group-role mapping: {} -> {}", request.groupName(), request.roleId());

                // Get user ID from security context
                String userId = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName();

                GroupRoleMapping mapping = mappingService.createMapping(
                                request.externalGroupId(),
                                request.groupName(),
                                request.roleId(),
                                request.priority() != null ? request.priority() : 0,
                                userId);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(GroupMappingResponse.from(mapping));
        }

        /**
         * Update an existing group-role mapping.
         */
        @PutMapping("/mappings/{mappingId}")
        @RequirePermission(resource = "group", action = "manage")
        public ResponseEntity<GroupMappingResponse> updateMapping(
                        @PathVariable UUID mappingId,
                        @Valid @RequestBody UpdateMappingRequest request) {
                log.info("Updating group-role mapping: {}", mappingId);

                GroupRoleMapping mapping = mappingService.updateMapping(
                                mappingId,
                                request.roleId(),
                                request.priority() != null ? request.priority() : 0);

                return ResponseEntity.ok(GroupMappingResponse.from(mapping));
        }

        /**
         * Delete a group-role mapping.
         */
        @DeleteMapping("/mappings/{mappingId}")
        @RequirePermission(resource = "group", action = "manage")
        public ResponseEntity<Void> deleteMapping(@PathVariable UUID mappingId) {
                log.info("Deleting group-role mapping: {}", mappingId);
                mappingService.deleteMapping(mappingId);
                return ResponseEntity.noContent().build();
        }

        /**
         * Get a specific mapping by ID.
         */
        @GetMapping("/mappings/{mappingId}")
        @RequirePermission(resource = "group", action = "read")
        public ResponseEntity<GroupMappingResponse> getMapping(@PathVariable UUID mappingId) {
                GroupRoleMapping mapping = mappingService.getMappingById(mappingId);
                return ResponseEntity.ok(GroupMappingResponse.from(mapping));
        }

        // ========== DTOs ==========

        public record CreateMappingRequest(
                        @NotBlank(message = "External group ID is required") String externalGroupId,

                        @NotBlank(message = "Group name is required") String groupName,

                        @NotBlank(message = "Role ID is required") String roleId,

                        Integer priority) {
        }

        public record UpdateMappingRequest(
                        @NotBlank(message = "Role ID is required") String roleId,

                        Integer priority) {
        }

        public record GroupMappingResponse(
                        UUID id,
                        String externalGroupId,
                        String groupName,
                        String roleId,
                        String roleName,
                        Integer priority,
                        Boolean autoAssign,
                        String createdBy) {
                public static GroupMappingResponse from(GroupRoleMapping mapping) {
                        return new GroupMappingResponse(
                                        mapping.getId(),
                                        mapping.getExternalGroupId(),
                                        mapping.getGroupName(),
                                        mapping.getRole().getId(),
                                        mapping.getRole().getName(),
                                        mapping.getPriority(),
                                        mapping.getAutoAssign(),
                                        mapping.getCreatedBy());
                }
        }
}
