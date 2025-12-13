package com.learning.platformservice.membership;

import com.learning.platformservice.membership.dto.AddMembershipRequest;
import com.learning.platformservice.membership.entity.MembershipRoleHint;
import com.learning.platformservice.membership.entity.MembershipStatus;
import com.learning.platformservice.membership.entity.UserTenantMembership;
import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.membership.service.MembershipService;
import com.learning.platformservice.tenant.repo.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MembershipService.
 * Tests multi-tenant login flow, membership lookup, and creation.
 */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

        @Mock
        private UserTenantMembershipRepository membershipRepository;

        @Mock
        private TenantRepository tenantRepository;

        @InjectMocks
        private MembershipService membershipService;

        private static final String TEST_EMAIL = "user@example.com";
        private static final String TEST_TENANT_ID = "tenant-123";

        @Test
        @DisplayName("findTenantsByEmail returns empty list when no memberships exist")
        void findTenantsByEmail_NoMemberships_ReturnsEmptyList() {
                // Given
                when(membershipRepository.findActiveByEmail(TEST_EMAIL)).thenReturn(List.of());

                // When
                var result = membershipService.findTenantsByEmail(TEST_EMAIL);

                // Then
                assertThat(result).isEmpty();
                verify(membershipRepository).findActiveByEmail(TEST_EMAIL);
        }

        @Test
        @DisplayName("addMembership creates new membership successfully")
        void addMembership_NewMembership_Success() {
                // Given
                AddMembershipRequest request = new AddMembershipRequest(
                                TEST_EMAIL,
                                "cognito-123",
                                TEST_TENANT_ID,
                                MembershipRoleHint.ADMIN.getValue(),
                                false, // isOwner
                                true, // isDefault
                                null // invitedBy
                );

                when(membershipRepository.existsByEmailAndTenant(TEST_EMAIL, TEST_TENANT_ID)).thenReturn(false);
                lenient().when(membershipRepository.countActiveByEmail(TEST_EMAIL)).thenReturn(0L);
                when(membershipRepository.save(any(UserTenantMembership.class)))
                                .thenAnswer(invocation -> {
                                        UserTenantMembership m = invocation.getArgument(0);
                                        return UserTenantMembership.builder()
                                                        .id(UUID.randomUUID())
                                                        .userEmail(m.getUserEmail())
                                                        .tenantId(m.getTenantId())
                                                        .roleHint(m.getRoleHint())
                                                        .isDefault(m.getIsDefault())
                                                        .isOwner(m.getIsOwner())
                                                        .status(m.getStatus())
                                                        .build();
                                });

                // When
                UserTenantMembership result = membershipService.addMembership(request);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getUserEmail()).isEqualTo(TEST_EMAIL.toLowerCase());
                assertThat(result.getTenantId()).isEqualTo(TEST_TENANT_ID);
                verify(membershipRepository).save(any(UserTenantMembership.class));
        }

        @Test
        @DisplayName("addMembership throws exception when membership already exists")
        void addMembership_AlreadyExists_ThrowsException() {
                // Given
                AddMembershipRequest request = new AddMembershipRequest(
                                TEST_EMAIL,
                                null,
                                TEST_TENANT_ID,
                                null,
                                false,
                                false,
                                null);

                when(membershipRepository.existsByEmailAndTenant(TEST_EMAIL, TEST_TENANT_ID)).thenReturn(true);

                // When/Then
                assertThatThrownBy(() -> membershipService.addMembership(request))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Membership already exists");

                verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("addMembership sets first membership as default automatically")
        void addMembership_FirstMembership_BecomesDefault() {
                // Given
                AddMembershipRequest request = new AddMembershipRequest(
                                TEST_EMAIL,
                                null,
                                TEST_TENANT_ID,
                                null,
                                false,
                                false, // Not requesting default
                                null);

                when(membershipRepository.existsByEmailAndTenant(TEST_EMAIL, TEST_TENANT_ID)).thenReturn(false);
                when(membershipRepository.countActiveByEmail(TEST_EMAIL)).thenReturn(0L); // First membership
                when(membershipRepository.save(any(UserTenantMembership.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                UserTenantMembership result = membershipService.addMembership(request);

                // Then
                assertThat(result.getIsDefault()).isTrue(); // Should be default since it's first
        }

        @Test
        @DisplayName("canCreatePersonalTenant returns true when no personal tenant exists")
        void canCreatePersonalTenant_NoExisting_ReturnsTrue() {
                // Given
                when(membershipRepository.hasPersonalTenant(TEST_EMAIL)).thenReturn(false);

                // When
                boolean result = membershipService.canCreatePersonalTenant(TEST_EMAIL);

                // Then
                assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canCreatePersonalTenant returns false when personal tenant exists")
        void canCreatePersonalTenant_ExistingPersonal_ReturnsFalse() {
                // Given
                when(membershipRepository.hasPersonalTenant(TEST_EMAIL)).thenReturn(true);

                // When
                boolean result = membershipService.canCreatePersonalTenant(TEST_EMAIL);

                // Then
                assertThat(result).isFalse();
        }
}
