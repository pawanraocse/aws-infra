package com.learning.authservice.authorization.controller;

import com.learning.authservice.authorization.controller.GroupRoleInternalController.RoleResolutionRequest;
import com.learning.authservice.authorization.controller.GroupRoleInternalController.RoleResolutionResponse;
import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.service.GroupRoleMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for GroupRoleInternalController.
 * Tests role resolution from SSO group memberships with edge cases.
 */
@ExtendWith(MockitoExtension.class)
class GroupRoleInternalControllerTest {

    @Mock
    private GroupRoleMappingService groupRoleMappingService;

    @InjectMocks
    private GroupRoleInternalController controller;

    private Role adminRole;
    private Role editorRole;
    private Role viewerRole;

    @BeforeEach
    void setUp() {
        adminRole = createRole("admin", "ADMIN", "admin");
        editorRole = createRole("editor", "EDITOR", "editor");
        viewerRole = createRole("viewer", "VIEWER", "viewer");
    }

    private Role createRole(String id, String name, String accessLevel) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setAccessLevel(accessLevel);
        return role;
    }

    @Nested
    @DisplayName("POST /internal/groups/resolve-role")
    class ResolveRoleTests {

        @Test
        @DisplayName("Should resolve role when matching groups found")
        void shouldResolveRoleWhenMatchingGroupsFound() {
            // Given
            List<String> groups = List.of("engineering", "developers");
            RoleResolutionRequest request = new RoleResolutionRequest(groups);

            Set<Role> roles = new LinkedHashSet<>();
            roles.add(editorRole);
            when(groupRoleMappingService.resolveRolesForGroups(groups)).thenReturn(roles);

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isTrue();
            assertThat(response.getBody().roleId()).isEqualTo("editor");
            assertThat(response.getBody().roleName()).isEqualTo("EDITOR");
            assertThat(response.getBody().accessLevel()).isEqualTo("editor");

            verify(groupRoleMappingService).resolveRolesForGroups(groups);
        }

        @Test
        @DisplayName("Should return highest priority role when multiple groups match")
        void shouldReturnHighestPriorityRoleWhenMultipleGroupsMatch() {
            // Given - Simulating that service returns roles ordered by priority
            List<String> groups = List.of("admins", "developers", "viewers");
            RoleResolutionRequest request = new RoleResolutionRequest(groups);

            // Admin role should come first (highest priority)
            Set<Role> roles = new LinkedHashSet<>();
            roles.add(adminRole); // Highest priority
            roles.add(editorRole); // Lower priority
            when(groupRoleMappingService.resolveRolesForGroups(groups)).thenReturn(roles);

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isTrue();
            assertThat(response.getBody().roleId()).isEqualTo("admin");
            assertThat(response.getBody().roleName()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should return no match when groups list is empty")
        void shouldReturnNoMatchWhenGroupsListIsEmpty() {
            // Given
            RoleResolutionRequest request = new RoleResolutionRequest(Collections.emptyList());

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isFalse();
            assertThat(response.getBody().roleId()).isNull();
            assertThat(response.getBody().roleName()).isNull();

            // Service should not be called for empty list
            verifyNoInteractions(groupRoleMappingService);
        }

        @Test
        @DisplayName("Should return no match when groups list is null")
        void shouldReturnNoMatchWhenGroupsListIsNull() {
            // Given
            RoleResolutionRequest request = new RoleResolutionRequest(null);

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isFalse();

            verifyNoInteractions(groupRoleMappingService);
        }

        @Test
        @DisplayName("Should return no match when no group mappings exist")
        void shouldReturnNoMatchWhenNoGroupMappingsExist() {
            // Given
            List<String> groups = List.of("unknown-group-1", "unknown-group-2");
            RoleResolutionRequest request = new RoleResolutionRequest(groups);

            when(groupRoleMappingService.resolveRolesForGroups(groups))
                    .thenReturn(Collections.emptySet());

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isFalse();
            assertThat(response.getBody().roleId()).isNull();
        }

        @Test
        @DisplayName("Should handle single group correctly")
        void shouldHandleSingleGroupCorrectly() {
            // Given
            List<String> groups = List.of("admins");
            RoleResolutionRequest request = new RoleResolutionRequest(groups);

            Set<Role> roles = Set.of(adminRole);
            when(groupRoleMappingService.resolveRolesForGroups(groups)).thenReturn(roles);

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isTrue();
            assertThat(response.getBody().roleId()).isEqualTo("admin");
        }

        @Test
        @DisplayName("Should handle group with special characters")
        void shouldHandleGroupWithSpecialCharacters() {
            // Given - Groups from Azure AD often have special formats
            List<String> groups = List.of(
                    "CN=Engineering Team,OU=Groups,DC=company,DC=com",
                    "azure-ad-group-id-12345");
            RoleResolutionRequest request = new RoleResolutionRequest(groups);

            Set<Role> roles = Set.of(editorRole);
            when(groupRoleMappingService.resolveRolesForGroups(groups)).thenReturn(roles);

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isTrue();
        }

        @Test
        @DisplayName("Should handle case-sensitive group matching")
        void shouldHandleCaseSensitiveGroupMatching() {
            // Given - Group matching should be case-sensitive
            List<String> groups = List.of("ADMINS", "Administrators");
            RoleResolutionRequest request = new RoleResolutionRequest(groups);

            // No match because mappings are for lowercase "admins"
            when(groupRoleMappingService.resolveRolesForGroups(groups))
                    .thenReturn(Collections.emptySet());

            // When
            ResponseEntity<RoleResolutionResponse> response = controller.resolveRole(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().matched()).isFalse();
        }
    }

    @Nested
    @DisplayName("RoleResolutionResponse factory methods")
    class RoleResolutionResponseTests {

        @Test
        @DisplayName("Should create matched response with all role fields")
        void shouldCreateMatchedResponseWithAllFields() {
            // Given
            Role role = createRole("custom-role", "CUSTOM_MANAGER", "editor");

            // When
            RoleResolutionResponse response = RoleResolutionResponse.of(role);

            // Then
            assertThat(response.matched()).isTrue();
            assertThat(response.roleId()).isEqualTo("custom-role");
            assertThat(response.roleName()).isEqualTo("CUSTOM_MANAGER");
            assertThat(response.accessLevel()).isEqualTo("editor");
        }

        @Test
        @DisplayName("Should create no-match response with null fields")
        void shouldCreateNoMatchResponseWithNullFields() {
            // When
            RoleResolutionResponse response = RoleResolutionResponse.noMatch();

            // Then
            assertThat(response.matched()).isFalse();
            assertThat(response.roleId()).isNull();
            assertThat(response.roleName()).isNull();
            assertThat(response.accessLevel()).isNull();
        }
    }
}
