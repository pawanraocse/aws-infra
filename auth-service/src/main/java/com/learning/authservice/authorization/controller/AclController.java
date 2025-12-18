package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.dto.AclEntryDto;
import com.learning.authservice.authorization.dto.GrantAccessRequest;
import com.learning.authservice.authorization.service.AclService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for ACL (Access Control List) operations.
 * Enables resource-level sharing and permission management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/acl")
@RequiredArgsConstructor
public class AclController {

    private final AclService aclService;

    /**
     * Grant access to a resource.
     * Admin/Manager can share resources with other users.
     */
    @PostMapping
    public ResponseEntity<AclEntryDto> grantAccess(
            @RequestBody GrantAccessRequest request,
            @RequestHeader("X-User-Id") String userId) {

        log.info("User {} granting access to resource {}", userId, request.getResourceId());
        AclEntryDto result = aclService.grantAccess(request, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Revoke access from a resource.
     */
    @DeleteMapping("/{aclEntryId}")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable UUID aclEntryId,
            @RequestHeader("X-User-Id") String userId) {

        log.info("User {} revoking ACL entry {}", userId, aclEntryId);
        aclService.revokeAccess(aclEntryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all users with access to a resource.
     */
    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<AclEntryDto>> getResourcePermissions(
            @PathVariable UUID resourceId) {

        List<AclEntryDto> entries = aclService.getResourcePermissions(resourceId);
        return ResponseEntity.ok(entries);
    }

    /**
     * Get all resources a user has access to.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AclEntryDto>> getUserPermissions(
            @PathVariable String userId) {

        List<AclEntryDto> entries = aclService.getUserPermissions(userId);
        return ResponseEntity.ok(entries);
    }

    /**
     * Check if user has a specific capability on a resource.
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkPermission(
            @RequestParam String userId,
            @RequestParam UUID resourceId,
            @RequestParam String capability) {

        boolean allowed = aclService.hasCapability(userId, resourceId, capability);
        return ResponseEntity.ok(Map.of(
                "allowed", allowed,
                "userId", userId,
                "resourceId", resourceId,
                "capability", capability));
    }

    /**
     * Get available role bundles with their capabilities.
     */
    @GetMapping("/role-bundles")
    public ResponseEntity<List<Map<String, Object>>> getRoleBundles() {
        List<Map<String, Object>> bundles = List.of(
                Map.of("name", "VIEWER", "description", "Read-only access",
                        "capabilities", List.of("read", "download", "view_metadata")),
                Map.of("name", "CONTRIBUTOR", "description", "Can add new content",
                        "capabilities", List.of("read", "download", "view_metadata", "upload", "create_folder")),
                Map.of("name", "EDITOR", "description", "Can modify content",
                        "capabilities", List.of("read", "download", "view_metadata", "upload", "create_folder",
                                "edit", "move", "rename", "delete_own")),
                Map.of("name", "MANAGER", "description", "Full control including sharing",
                        "capabilities", List.of("read", "download", "view_metadata", "upload", "create_folder",
                                "edit", "move", "rename", "delete_own", "delete_any", "share", "manage_access")));
        return ResponseEntity.ok(bundles);
    }
}
