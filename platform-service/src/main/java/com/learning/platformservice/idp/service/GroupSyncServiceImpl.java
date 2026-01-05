package com.learning.platformservice.idp.service;

import com.learning.common.dto.IdpType;
import com.learning.platformservice.idp.entity.IdpGroup;
import com.learning.platformservice.idp.repository.IdpGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of GroupSyncService.
 * Handles syncing groups from IdP claims during SSO login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupSyncServiceImpl implements GroupSyncService {

    private final IdpGroupRepository idpGroupRepository;

    @Override
    @Transactional
    public List<IdpGroup> syncGroupsFromClaims(String tenantId, List<String> groupClaims, IdpType idpType) {
        if (groupClaims == null || groupClaims.isEmpty()) {
            log.debug("No groups to sync for tenant {}", tenantId);
            return List.of();
        }

        log.info("Syncing {} groups for tenant {} from {}", groupClaims.size(), tenantId, idpType);

        List<IdpGroup> syncedGroups = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (String groupClaim : groupClaims) {
            if (groupClaim == null || groupClaim.isBlank()) {
                continue;
            }

            String externalGroupId = groupClaim.trim();
            String groupName = extractGroupName(externalGroupId);

            Optional<IdpGroup> existingGroup = idpGroupRepository
                    .findByTenantIdAndExternalGroupId(tenantId, externalGroupId);

            if (existingGroup.isPresent()) {
                // Update last synced timestamp
                IdpGroup group = existingGroup.get();
                group.setLastSyncedAt(now);
                syncedGroups.add(idpGroupRepository.save(group));
                log.debug("Updated existing group: {}", groupName);
            } else {
                // Create new group
                IdpGroup newGroup = IdpGroup.builder()
                        .tenantId(tenantId)
                        .externalGroupId(externalGroupId)
                        .groupName(groupName)
                        .idpType(idpType)
                        .lastSyncedAt(now)
                        .build();
                syncedGroups.add(idpGroupRepository.save(newGroup));
                log.info("Created new group: {} for tenant {}", groupName, tenantId);
            }
        }

        return syncedGroups;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdpGroup> getGroupsForTenant(String tenantId) {
        return idpGroupRepository.findByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public IdpGroup getGroupByExternalId(String tenantId, String externalGroupId) {
        return idpGroupRepository.findByTenantIdAndExternalGroupId(tenantId, externalGroupId)
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteAllGroupsForTenant(String tenantId) {
        log.info("Deleting all IdP groups for tenant {}", tenantId);
        idpGroupRepository.deleteByTenantId(tenantId);
    }

    /**
     * Extract a human-readable name from the external group ID.
     * Handles common formats like SAML DN and OIDC group names.
     */
    private String extractGroupName(String externalGroupId) {
        // Handle SAML DN format: "cn=Engineering,ou=Groups,dc=company,dc=com"
        if (externalGroupId.toLowerCase().startsWith("cn=")) {
            int commaIndex = externalGroupId.indexOf(',');
            if (commaIndex > 3) {
                return externalGroupId.substring(3, commaIndex);
            }
        }

        // Handle simple group names
        // Remove common prefixes
        String name = externalGroupId;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        if (name.contains("\\")) {
            name = name.substring(name.lastIndexOf('\\') + 1);
        }

        // Truncate if too long
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }

        return name;
    }
}
