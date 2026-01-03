package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.GroupRoleMapping;
import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.repository.GroupRoleMappingRepository;
import com.learning.authservice.authorization.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of GroupRoleMappingService.
 * Manages group-to-role mappings and resolves roles during SSO login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupRoleMappingServiceImpl implements GroupRoleMappingService {

    private static final String CACHE_NAME = "groupRoleMappings";

    private final GroupRoleMappingRepository mappingRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public GroupRoleMapping createMapping(String externalGroupId, String groupName, String roleId,
            int priority, String createdBy) {
        // Validate role exists
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        // Check for duplicate
        if (mappingRepository.existsByExternalGroupId(externalGroupId)) {
            throw new IllegalArgumentException("Mapping already exists for group: " + externalGroupId);
        }

        GroupRoleMapping mapping = GroupRoleMapping.builder()
                .externalGroupId(externalGroupId)
                .groupName(groupName)
                .role(role)
                .priority(priority)
                .autoAssign(true)
                .createdBy(createdBy)
                .build();

        GroupRoleMapping saved = mappingRepository.save(mapping);
        log.info("Created group-role mapping: {} -> {} (priority: {})", groupName, roleId, priority);
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public GroupRoleMapping updateMapping(UUID mappingId, String roleId, int priority) {
        GroupRoleMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        mapping.setRole(role);
        mapping.setPriority(priority);

        GroupRoleMapping updated = mappingRepository.save(mapping);
        log.info("Updated group-role mapping: {} -> {} (priority: {})",
                mapping.getGroupName(), roleId, priority);
        return updated;
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteMapping(UUID mappingId) {
        if (!mappingRepository.existsById(mappingId)) {
            throw new IllegalArgumentException("Mapping not found: " + mappingId);
        }
        mappingRepository.deleteById(mappingId);
        log.info("Deleted group-role mapping: {}", mappingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupRoleMapping> getAllMappings() {
        return mappingRepository.findAllByOrderByPriorityDesc();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#externalGroupIds.hashCode()")
    public Set<Role> resolveRolesForGroups(List<String> externalGroupIds) {
        if (externalGroupIds == null || externalGroupIds.isEmpty()) {
            return Set.of();
        }

        List<GroupRoleMapping> mappings = mappingRepository
                .findAutoAssignMappingsByExternalGroupIds(externalGroupIds);

        if (mappings.isEmpty()) {
            log.debug("No role mappings found for groups: {}", externalGroupIds);
            return Set.of();
        }

        // Collect all unique roles (mappings are already sorted by priority)
        Set<Role> roles = mappings.stream()
                .map(GroupRoleMapping::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.debug("Resolved {} roles for {} groups", roles.size(), externalGroupIds.size());
        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public GroupRoleMapping getMappingById(UUID mappingId) {
        return mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mappingExists(String externalGroupId) {
        return mappingRepository.existsByExternalGroupId(externalGroupId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'resolve_' + #externalGroupIds.hashCode()")
    public java.util.Optional<String> resolveRoleFromGroups(List<String> externalGroupIds) {
        if (externalGroupIds == null || externalGroupIds.isEmpty()) {
            return java.util.Optional.empty();
        }

        // Query mappings for all groups, sorted by priority (highest first)
        List<GroupRoleMapping> mappings = mappingRepository
                .findAutoAssignMappingsByExternalGroupIds(externalGroupIds);

        if (mappings.isEmpty()) {
            log.debug("No role mappings found for groups: {}", externalGroupIds);
            return java.util.Optional.empty();
        }

        // Return highest priority role
        String roleId = mappings.get(0).getRole().getId();
        log.info("Resolved role '{}' from group mappings for groups: {}", roleId, externalGroupIds);
        return java.util.Optional.of(roleId);
    }
}
