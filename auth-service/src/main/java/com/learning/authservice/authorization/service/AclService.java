package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.AclEntry;
import com.learning.authservice.authorization.dto.AclEntryDto;
import com.learning.authservice.authorization.dto.GrantAccessRequest;
import com.learning.authservice.authorization.repository.AclEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing resource-level access control (ACLs).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AclService {

    private final AclEntryRepository aclEntryRepository;

    /**
     * Role bundle permission hierarchy.
     * Higher bundles include all permissions of lower ones.
     */
    private static final Map<String, Set<String>> ROLE_CAPABILITIES = Map.of(
            "VIEWER", Set.of("read", "download", "view_metadata"),
            "CONTRIBUTOR", Set.of("read", "download", "view_metadata", "upload", "create_folder"),
            "EDITOR", Set.of("read", "download", "view_metadata", "upload", "create_folder",
                    "edit", "move", "rename", "delete_own"),
            "MANAGER", Set.of("read", "download", "view_metadata", "upload", "create_folder",
                    "edit", "move", "rename", "delete_own", "delete_any", "share", "manage_access"));

    private static final List<String> ROLE_HIERARCHY = List.of("VIEWER", "CONTRIBUTOR", "EDITOR", "MANAGER");

    /**
     * Grant access to a resource.
     */
    @Transactional
    public AclEntryDto grantAccess(@NonNull GrantAccessRequest request, @NonNull String grantedBy) {
        log.info("Granting {} access to resource {} for {} {}",
                request.getRoleBundle(), request.getResourceId(),
                request.getPrincipalType(), request.getPrincipalId());

        // Check if entry already exists
        Optional<AclEntry> existing = aclEntryRepository.findByResourceIdAndPrincipalTypeAndPrincipalId(
                request.getResourceId(), request.getPrincipalType(), request.getPrincipalId());

        AclEntry entry;
        if (existing.isPresent()) {
            // Update existing entry
            entry = existing.get();
            entry.setRoleBundle(request.getRoleBundle());
            entry.setExpiresAt(request.getExpiresAt());
            entry.setGrantedBy(grantedBy);
            entry.setGrantedAt(Instant.now());
        } else {
            // Create new entry
            entry = AclEntry.builder()
                    .resourceId(request.getResourceId())
                    .resourceType(request.getResourceType())
                    .principalType(request.getPrincipalType())
                    .principalId(request.getPrincipalId())
                    .roleBundle(request.getRoleBundle())
                    .grantedBy(grantedBy)
                    .expiresAt(request.getExpiresAt())
                    .build();
        }

        AclEntry saved = aclEntryRepository.save(entry);
        return toDto(saved);
    }

    /**
     * Revoke access from a resource.
     */
    @Transactional
    public void revokeAccess(@NonNull UUID aclEntryId) {
        log.info("Revoking ACL entry {}", aclEntryId);
        aclEntryRepository.deleteById(aclEntryId);
    }

    /**
     * Get all users/groups with access to a resource.
     */
    @Transactional(readOnly = true)
    public List<AclEntryDto> getResourcePermissions(@NonNull UUID resourceId) {
        return aclEntryRepository.findByResourceId(resourceId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get all resources a user has access to.
     */
    @Transactional(readOnly = true)
    public List<AclEntryDto> getUserPermissions(@NonNull String userId) {
        return aclEntryRepository.findValidEntriesForPrincipal("USER", userId, Instant.now()).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Check if a user has a specific capability on a resource.
     * 
     * @param userId     User ID
     * @param resourceId Resource ID
     * @param capability The capability to check (e.g., "read", "edit", "delete")
     * @return true if user has the capability
     */
    @Transactional(readOnly = true)
    public boolean hasCapability(@NonNull String userId, @NonNull UUID resourceId, @NonNull String capability) {
        Optional<AclEntry> entry = aclEntryRepository.findValidEntry(resourceId, "USER", userId, Instant.now());

        if (entry.isEmpty()) {
            // TODO: Check group-based access
            return false;
        }

        String roleBundle = entry.get().getRoleBundle();
        Set<String> capabilities = ROLE_CAPABILITIES.getOrDefault(roleBundle, Set.of());
        return capabilities.contains(capability.toLowerCase());
    }

    /**
     * Check if user has at least a certain role level (e.g., EDITOR or higher).
     */
    @Transactional(readOnly = true)
    public boolean hasRoleLevel(@NonNull String userId, @NonNull UUID resourceId, @NonNull String minRoleBundle) {
        Optional<AclEntry> entry = aclEntryRepository.findValidEntry(resourceId, "USER", userId, Instant.now());

        if (entry.isEmpty()) {
            return false;
        }

        int userLevel = ROLE_HIERARCHY.indexOf(entry.get().getRoleBundle());
        int requiredLevel = ROLE_HIERARCHY.indexOf(minRoleBundle);

        return userLevel >= requiredLevel;
    }

    private AclEntryDto toDto(AclEntry entry) {
        return AclEntryDto.builder()
                .id(entry.getId())
                .resourceId(entry.getResourceId())
                .resourceType(entry.getResourceType())
                .principalType(entry.getPrincipalType())
                .principalId(entry.getPrincipalId())
                .roleBundle(entry.getRoleBundle())
                .grantedBy(entry.getGrantedBy())
                .grantedAt(entry.getGrantedAt())
                .expiresAt(entry.getExpiresAt())
                .build();
    }
}
