package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.GroupRoleMapping;
import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.repository.GroupRoleMappingRepository;
import com.learning.authservice.authorization.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupRoleMappingServiceImpl.
 * Tests CRUD operations and role resolution.
 */
@ExtendWith(MockitoExtension.class)
class GroupRoleMappingServiceTest {

    @Mock
    private GroupRoleMappingRepository mappingRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private GroupRoleMappingServiceImpl service;

    @Captor
    private ArgumentCaptor<GroupRoleMapping> mappingCaptor;

    private Role tenantAdminRole;
    private Role tenantEditorRole;
    private Role tenantViewerRole;

    @BeforeEach
    void setUp() {
        tenantAdminRole = new Role();
        tenantAdminRole.setId("admin");
        tenantAdminRole.setName("Admin");

        tenantEditorRole = new Role();
        tenantEditorRole.setId("editor");
        tenantEditorRole.setName("Editor");

        tenantViewerRole = new Role();
        tenantViewerRole.setId("viewer");
        tenantViewerRole.setName("Viewer");
    }

    @Nested
    @DisplayName("createMapping")
    class CreateMapping {

        @Test
        @DisplayName("should create mapping successfully")
        void shouldCreateMappingSuccessfully() {
            // Given
            String externalGroupId = "cn=Engineering,dc=company";
            String groupName = "Engineering";
            when(roleRepository.findById("admin")).thenReturn(Optional.of(tenantAdminRole));
            when(mappingRepository.existsByExternalGroupId(externalGroupId)).thenReturn(false);
            when(mappingRepository.save(any(GroupRoleMapping.class))).thenAnswer(inv -> {
                GroupRoleMapping m = inv.getArgument(0);
                m.setId(UUID.randomUUID());
                return m;
            });

            // When
            GroupRoleMapping result = service.createMapping(
                    externalGroupId, groupName, "admin", 10, "admin-user");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getExternalGroupId()).isEqualTo(externalGroupId);
            assertThat(result.getGroupName()).isEqualTo(groupName);
            assertThat(result.getRole()).isEqualTo(tenantAdminRole);
            assertThat(result.getPriority()).isEqualTo(10);
        }

        @Test
        @DisplayName("should throw when role not found")
        void shouldThrowWhenRoleNotFound() {
            // Given
            when(roleRepository.findById("invalid-role")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.createMapping(
                    "group-id", "Group", "invalid-role", 0, "user"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Role not found");
        }

        @Test
        @DisplayName("should throw when mapping already exists")
        void shouldThrowWhenMappingExists() {
            // Given
            when(roleRepository.findById("admin")).thenReturn(Optional.of(tenantAdminRole));
            when(mappingRepository.existsByExternalGroupId("existing-group")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> service.createMapping(
                    "existing-group", "Group", "admin", 0, "user"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("updateMapping")
    class UpdateMapping {

        @Test
        @DisplayName("should update mapping successfully")
        void shouldUpdateMappingSuccessfully() {
            // Given
            UUID mappingId = UUID.randomUUID();
            GroupRoleMapping existing = GroupRoleMapping.builder()
                    .id(mappingId)
                    .externalGroupId("group-1")
                    .groupName("Group 1")
                    .role(tenantViewerRole)
                    .priority(0)
                    .build();

            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(existing));
            when(roleRepository.findById("editor")).thenReturn(Optional.of(tenantEditorRole));
            when(mappingRepository.save(any(GroupRoleMapping.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            GroupRoleMapping result = service.updateMapping(mappingId, "editor", 50);

            // Then
            assertThat(result.getRole()).isEqualTo(tenantEditorRole);
            assertThat(result.getPriority()).isEqualTo(50);
        }

        @Test
        @DisplayName("should throw when mapping not found")
        void shouldThrowWhenMappingNotFound() {
            // Given
            UUID mappingId = UUID.randomUUID();
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.updateMapping(mappingId, "admin", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mapping not found");
        }
    }

    @Nested
    @DisplayName("deleteMapping")
    class DeleteMapping {

        @Test
        @DisplayName("should delete mapping successfully")
        void shouldDeleteMappingSuccessfully() {
            // Given
            UUID mappingId = UUID.randomUUID();
            when(mappingRepository.existsById(mappingId)).thenReturn(true);

            // When
            service.deleteMapping(mappingId);

            // Then
            verify(mappingRepository).deleteById(mappingId);
        }

        @Test
        @DisplayName("should throw when mapping not found")
        void shouldThrowWhenMappingNotFoundOnDelete() {
            // Given
            UUID mappingId = UUID.randomUUID();
            when(mappingRepository.existsById(mappingId)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.deleteMapping(mappingId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mapping not found");
        }
    }

    @Nested
    @DisplayName("resolveRolesForGroups")
    class ResolveRolesForGroups {

        @Test
        @DisplayName("should resolve roles from group mappings")
        void shouldResolveRolesFromMappings() {
            // Given
            List<String> groupIds = Arrays.asList("group-1", "group-2");
            List<GroupRoleMapping> mappings = Arrays.asList(
                    GroupRoleMapping.builder().role(tenantAdminRole).priority(100).autoAssign(true).build(),
                    GroupRoleMapping.builder().role(tenantEditorRole).priority(50).autoAssign(true).build());
            when(mappingRepository.findAutoAssignMappingsByExternalGroupIds(groupIds)).thenReturn(mappings);

            // When
            Set<Role> result = service.resolveRolesForGroups(groupIds);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).contains(tenantAdminRole, tenantEditorRole);
        }

        @Test
        @DisplayName("should return empty set for empty group list")
        void shouldReturnEmptySetForEmptyGroupList() {
            // When
            Set<Role> result = service.resolveRolesForGroups(Collections.emptyList());

            // Then
            assertThat(result).isEmpty();
            verify(mappingRepository, never()).findAutoAssignMappingsByExternalGroupIds(any());
        }

        @Test
        @DisplayName("should return empty set for null group list")
        void shouldReturnEmptySetForNullGroupList() {
            // When
            Set<Role> result = service.resolveRolesForGroups(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty set when no mappings found")
        void shouldReturnEmptySetWhenNoMappingsFound() {
            // Given
            List<String> groupIds = Arrays.asList("unknown-group");
            when(mappingRepository.findAutoAssignMappingsByExternalGroupIds(groupIds))
                    .thenReturn(Collections.emptyList());

            // When
            Set<Role> result = service.resolveRolesForGroups(groupIds);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllMappings")
    class GetAllMappings {

        @Test
        @DisplayName("should return all mappings sorted by priority")
        void shouldReturnAllMappingsSortedByPriority() {
            // Given
            List<GroupRoleMapping> mappings = Arrays.asList(
                    GroupRoleMapping.builder().priority(100).build(),
                    GroupRoleMapping.builder().priority(50).build());
            when(mappingRepository.findAllByOrderByPriorityDesc()).thenReturn(mappings);

            // When
            List<GroupRoleMapping> result = service.getAllMappings();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPriority()).isEqualTo(100);
        }
    }
}
