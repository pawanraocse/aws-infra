package com.learning.platformservice.idp.controller;

import com.learning.platformservice.idp.entity.IdpGroup;
import com.learning.platformservice.idp.service.GroupSyncService;
import com.learning.platformservice.tenant.entity.IdpType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal controller for group synchronization.
 * Called by Lambda functions during SSO login flow.
 * 
 * NOTE: This is an internal endpoint - no authentication required.
 * Should only be accessible from within the VPC.
 */
@RestController
@RequestMapping("/internal/groups")
@RequiredArgsConstructor
@Slf4j
public class GroupInternalController {

    private final GroupSyncService groupSyncService;

    /**
     * Sync groups from SSO login claims.
     * Called by the PreTokenGeneration Lambda when a user logs in via SSO.
     *
     * @param request Group sync request containing tenant ID and group claims
     * @return List of synced groups with their IDs
     */
    @PostMapping("/sync")
    public ResponseEntity<GroupSyncResponse> syncGroups(@Valid @RequestBody GroupSyncRequest request) {
        log.info("Syncing {} groups for tenant {} from {}",
                request.groups().size(), request.tenantId(), request.idpType());

        IdpType idpType = IdpType.valueOf(request.idpType().toUpperCase());
        List<IdpGroup> syncedGroups = groupSyncService.syncGroupsFromClaims(
                request.tenantId(),
                request.groups(),
                idpType);

        List<GroupInfo> groupInfos = syncedGroups.stream()
                .map(g -> new GroupInfo(
                        g.getId().toString(),
                        g.getExternalGroupId(),
                        g.getGroupName()))
                .toList();

        return ResponseEntity.ok(new GroupSyncResponse(groupInfos));
    }

    /**
     * Get all groups for a tenant.
     * Used by auth-service to lookup groups for role resolution.
     */
    @GetMapping
    public ResponseEntity<List<GroupInfo>> getGroupsForTenant(@RequestParam String tenantId) {
        log.debug("Getting groups for tenant {}", tenantId);

        List<IdpGroup> groups = groupSyncService.getGroupsForTenant(tenantId);
        List<GroupInfo> groupInfos = groups.stream()
                .map(g -> new GroupInfo(
                        g.getId().toString(),
                        g.getExternalGroupId(),
                        g.getGroupName()))
                .toList();

        return ResponseEntity.ok(groupInfos);
    }

    // ========== DTOs ==========

    public record GroupSyncRequest(
            @NotBlank(message = "Tenant ID is required") String tenantId,

            @NotNull(message = "Groups list is required") List<String> groups,

            @NotBlank(message = "IdP type is required") String idpType) {
    }

    public record GroupSyncResponse(
            List<GroupInfo> syncedGroups) {
    }

    public record GroupInfo(
            String id,
            String externalGroupId,
            String groupName) {
    }
}
