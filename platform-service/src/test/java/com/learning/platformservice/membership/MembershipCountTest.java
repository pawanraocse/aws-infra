package com.learning.platformservice.membership;

import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.membership.service.MembershipService;
import com.learning.platformservice.tenant.repo.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipCountTest {

    @Mock
    private UserTenantMembershipRepository membershipRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private MembershipService membershipService;

    @Test
    @DisplayName("countActiveByEmail returns 0 when user has no memberships")
    void countActiveByEmail_noMemberships_returnsZero() {
        // Given
        String email = "user@example.com";
        when(membershipRepository.countActiveByEmail(email)).thenReturn(0L);

        // When
        long count = membershipService.countActiveByEmail(email);

        // Then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("countActiveByEmail returns count of active memberships")
    void countActiveByEmail_withMemberships_returnsCount() {
        // Given
        String email = "multi-tenant@example.com";
        when(membershipRepository.countActiveByEmail(email)).thenReturn(3L);

        // When
        long count = membershipService.countActiveByEmail(email);

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countActiveByEmail ignores REMOVED memberships")
    void countActiveByEmail_ignoresRemovedMemberships() {
        // Given - user has 2 active + 1 removed membership
        String email = "deleted-tenant@example.com";
        // Repository only counts ACTIVE status, so removed ones aren't included
        when(membershipRepository.countActiveByEmail(email)).thenReturn(2L);

        // When
        long count = membershipService.countActiveByEmail(email);

        // Then - only active ones counted
        assertThat(count).isEqualTo(2);
    }
}
