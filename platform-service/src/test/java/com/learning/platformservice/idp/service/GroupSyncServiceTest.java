package com.learning.platformservice.idp.service;

import com.learning.common.dto.IdpType;
import com.learning.platformservice.idp.entity.IdpGroup;
import com.learning.platformservice.idp.repository.IdpGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupSyncServiceImpl.
 * Tests group synchronization from SSO claims.
 */
@ExtendWith(MockitoExtension.class)
class GroupSyncServiceTest {

    @Mock
    private IdpGroupRepository idpGroupRepository;

    @InjectMocks
    private GroupSyncServiceImpl groupSyncService;

    @Captor
    private ArgumentCaptor<IdpGroup> groupCaptor;

    private static final String TENANT_ID = "test-tenant-123";

    @Nested
    @DisplayName("syncGroupsFromClaims")
    class SyncGroupsFromClaims {

        @Test
        @DisplayName("should create new groups when none exist")
        void shouldCreateNewGroups() {
            // Given
            List<String> groupClaims = Arrays.asList("cn=Engineering,dc=company", "cn=Marketing,dc=company");
            when(idpGroupRepository.findByTenantIdAndExternalGroupId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(idpGroupRepository.save(any(IdpGroup.class))).thenAnswer(inv -> {
                IdpGroup g = inv.getArgument(0);
                g.setId(UUID.randomUUID());
                return g;
            });

            // When
            List<IdpGroup> result = groupSyncService.syncGroupsFromClaims(TENANT_ID, groupClaims, IdpType.SAML);

            // Then
            assertThat(result).hasSize(2);
            verify(idpGroupRepository, times(2)).save(groupCaptor.capture());

            List<IdpGroup> savedGroups = groupCaptor.getAllValues();
            assertThat(savedGroups).extracting(IdpGroup::getGroupName)
                    .containsExactlyInAnyOrder("Engineering", "Marketing");
        }

        @Test
        @DisplayName("should return existing groups without creating duplicates")
        void shouldReturnExistingGroupsWithoutDuplicates() {
            // Given
            String groupClaim = "cn=Engineering,dc=company";
            IdpGroup existingGroup = IdpGroup.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .externalGroupId(groupClaim)
                    .groupName("Engineering")
                    .idpType(IdpType.SAML)
                    .build();

            when(idpGroupRepository.findByTenantIdAndExternalGroupId(TENANT_ID, groupClaim))
                    .thenReturn(Optional.of(existingGroup));
            // Service calls save to update lastSyncedAt
            when(idpGroupRepository.save(any(IdpGroup.class))).thenReturn(existingGroup);

            // When
            List<IdpGroup> result = groupSyncService.syncGroupsFromClaims(
                    TENANT_ID, Collections.singletonList(groupClaim), IdpType.SAML);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(existingGroup.getId());
            // Verify save was called once (to update lastSyncedAt)
            verify(idpGroupRepository, times(1)).save(any(IdpGroup.class));
        }

        @Test
        @DisplayName("should handle empty group list")
        void shouldHandleEmptyGroupList() {
            // When
            List<IdpGroup> result = groupSyncService.syncGroupsFromClaims(
                    TENANT_ID, Collections.emptyList(), IdpType.OIDC);

            // Then
            assertThat(result).isEmpty();
            verify(idpGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("should handle null group list")
        void shouldHandleNullGroupList() {
            // When
            List<IdpGroup> result = groupSyncService.syncGroupsFromClaims(TENANT_ID, null, IdpType.OIDC);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Group Name Extraction")
    class GroupNameExtraction {

        @Test
        @DisplayName("should extract CN from LDAP-style DN")
        void shouldExtractCnFromLdapDn() {
            // Given
            String groupClaim = "cn=Engineering Team,ou=Groups,dc=company,dc=com";
            when(idpGroupRepository.findByTenantIdAndExternalGroupId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(idpGroupRepository.save(any(IdpGroup.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            groupSyncService.syncGroupsFromClaims(TENANT_ID, Collections.singletonList(groupClaim), IdpType.SAML);

            // Then
            verify(idpGroupRepository).save(groupCaptor.capture());
            assertThat(groupCaptor.getValue().getGroupName()).isEqualTo("Engineering Team");
        }

        @Test
        @DisplayName("should use full string when not LDAP format")
        void shouldUseFullStringWhenNotLdapFormat() {
            // Given
            String groupClaim = "simple-group-name";
            when(idpGroupRepository.findByTenantIdAndExternalGroupId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(idpGroupRepository.save(any(IdpGroup.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            groupSyncService.syncGroupsFromClaims(TENANT_ID, Collections.singletonList(groupClaim), IdpType.OIDC);

            // Then
            verify(idpGroupRepository).save(groupCaptor.capture());
            assertThat(groupCaptor.getValue().getGroupName()).isEqualTo("simple-group-name");
        }
    }

    @Nested
    @DisplayName("getGroupsForTenant")
    class GetGroupsForTenant {

        @Test
        @DisplayName("should return all groups for tenant")
        void shouldReturnAllGroupsForTenant() {
            // Given
            List<IdpGroup> expectedGroups = Arrays.asList(
                    IdpGroup.builder().id(UUID.randomUUID()).groupName("Group1").build(),
                    IdpGroup.builder().id(UUID.randomUUID()).groupName("Group2").build());
            when(idpGroupRepository.findByTenantId(TENANT_ID)).thenReturn(expectedGroups);

            // When
            List<IdpGroup> result = groupSyncService.getGroupsForTenant(TENANT_ID);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(expectedGroups);
        }
    }
}
