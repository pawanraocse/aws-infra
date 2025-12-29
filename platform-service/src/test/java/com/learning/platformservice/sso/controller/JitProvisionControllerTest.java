package com.learning.platformservice.sso.controller;

import com.learning.platformservice.membership.entity.MembershipStatus;
import com.learning.platformservice.membership.entity.UserTenantMembership;
import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JitProvisionController.
 * Tests JIT user provisioning for SSO login flow.
 */
@ExtendWith(MockitoExtension.class)
class JitProvisionControllerTest {

    @Mock
    private UserTenantMembershipRepository membershipRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private JitProvisionController jitProvisionController;

    @Captor
    private ArgumentCaptor<UserTenantMembership> membershipCaptor;

    private static final String TENANT_ID = "test-tenant-123";
    private static final String USER_EMAIL = "user@example.com";
    private static final String COGNITO_USER_ID = "cognito-sub-12345";

    @Nested
    @DisplayName("checkUserExists")
    class CheckUserExists {

        @Test
        @DisplayName("should return true when active user exists")
        void shouldReturnTrueWhenActiveUserExists() {
            // Given
            UserTenantMembership membership = createMembership(MembershipStatus.ACTIVE);
            when(membershipRepository.findByEmailAndTenant(USER_EMAIL, TENANT_ID))
                    .thenReturn(Optional.of(membership));

            // When
            ResponseEntity<JitProvisionController.UserExistsResponse> response = jitProvisionController
                    .checkUserExists(TENANT_ID, USER_EMAIL);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().exists()).isTrue();
            assertThat(response.getBody().tenantId()).isEqualTo(TENANT_ID);
            assertThat(response.getBody().email()).isEqualTo(USER_EMAIL);
        }

        @Test
        @DisplayName("should return false when user does not exist")
        void shouldReturnFalseWhenUserDoesNotExist() {
            // Given
            when(membershipRepository.findByEmailAndTenant(USER_EMAIL, TENANT_ID))
                    .thenReturn(Optional.empty());

            // When
            ResponseEntity<JitProvisionController.UserExistsResponse> response = jitProvisionController
                    .checkUserExists(TENANT_ID, USER_EMAIL);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().exists()).isFalse();
        }

        @Test
        @DisplayName("should return false when user exists but is not active")
        void shouldReturnFalseWhenUserNotActive() {
            // Given
            UserTenantMembership membership = createMembership(MembershipStatus.SUSPENDED);
            when(membershipRepository.findByEmailAndTenant(USER_EMAIL, TENANT_ID))
                    .thenReturn(Optional.of(membership));

            // When
            ResponseEntity<JitProvisionController.UserExistsResponse> response = jitProvisionController
                    .checkUserExists(TENANT_ID, USER_EMAIL);

            // Then
            assertThat(response.getBody().exists()).isFalse();
        }
    }

    @Nested
    @DisplayName("provisionUser")
    class ProvisionUser {

        @Test
        @DisplayName("should create membership for new SSO user")
        void shouldCreateMembershipForNewUser() {
            // Given
            Tenant tenant = createTenant();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(membershipRepository.findByEmailAndTenant(USER_EMAIL, TENANT_ID))
                    .thenReturn(Optional.empty());
            when(membershipRepository.save(any(UserTenantMembership.class)))
                    .thenAnswer(inv -> {
                        UserTenantMembership m = inv.getArgument(0);
                        m.setId(UUID.randomUUID());
                        return m;
                    });

            JitProvisionController.JitProvisionRequest request = new JitProvisionController.JitProvisionRequest(
                    TENANT_ID, USER_EMAIL, COGNITO_USER_ID,
                    List.of("Engineering", "Marketing"),
                    "SAML", "user");

            // When
            ResponseEntity<JitProvisionController.JitProvisionResponse> response = jitProvisionController
                    .provisionUser(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().email()).isEqualTo(USER_EMAIL);

            verify(membershipRepository).save(membershipCaptor.capture());
            UserTenantMembership savedMembership = membershipCaptor.getValue();
            assertThat(savedMembership.getUserEmail()).isEqualTo(USER_EMAIL);
            assertThat(savedMembership.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(savedMembership.getCognitoUserId()).isEqualTo(COGNITO_USER_ID);
            assertThat(savedMembership.getRoleHint()).isEqualTo("user");
            assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(savedMembership.getIsOwner()).isFalse();
        }

        @Test
        @DisplayName("should use default role when not specified")
        void shouldUseDefaultRoleWhenNotSpecified() {
            // Given
            Tenant tenant = createTenant();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(membershipRepository.findByEmailAndTenant(USER_EMAIL, TENANT_ID))
                    .thenReturn(Optional.empty());
            when(membershipRepository.save(any(UserTenantMembership.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            JitProvisionController.JitProvisionRequest request = new JitProvisionController.JitProvisionRequest(
                    TENANT_ID, USER_EMAIL, COGNITO_USER_ID,
                    List.of(), "OIDC", null // No role specified
            );

            // When
            jitProvisionController.provisionUser(request);

            // Then
            verify(membershipRepository).save(membershipCaptor.capture());
            assertThat(membershipCaptor.getValue().getRoleHint()).isEqualTo("user");
        }

        @Test
        @DisplayName("should return success=false when user already exists")
        void shouldReturnFalseWhenUserExists() {
            // Given
            Tenant tenant = createTenant();
            UserTenantMembership existingMembership = createMembership(MembershipStatus.ACTIVE);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(membershipRepository.findByEmailAndTenant(USER_EMAIL, TENANT_ID))
                    .thenReturn(Optional.of(existingMembership));

            JitProvisionController.JitProvisionRequest request = new JitProvisionController.JitProvisionRequest(
                    TENANT_ID, USER_EMAIL, COGNITO_USER_ID,
                    List.of(), "SAML", "user");

            // When
            ResponseEntity<JitProvisionController.JitProvisionResponse> response = jitProvisionController
                    .provisionUser(request);

            // Then
            assertThat(response.getBody().success()).isFalse();
            assertThat(response.getBody().message()).contains("already exists");
            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            // Given
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            JitProvisionController.JitProvisionRequest request = new JitProvisionController.JitProvisionRequest(
                    TENANT_ID, USER_EMAIL, COGNITO_USER_ID,
                    List.of(), "SAML", "user");

            // When/Then
            assertThatThrownBy(() -> jitProvisionController.provisionUser(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tenant not found");
        }
    }

    // ========== Helper Methods ==========

    private Tenant createTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Test Tenant");
        tenant.setSsoEnabled(true);
        return tenant;
    }

    private UserTenantMembership createMembership(MembershipStatus status) {
        UserTenantMembership membership = new UserTenantMembership();
        membership.setId(UUID.randomUUID());
        membership.setUserEmail(USER_EMAIL);
        membership.setTenantId(TENANT_ID);
        membership.setCognitoUserId(COGNITO_USER_ID);
        membership.setRoleHint("user");
        membership.setStatus(status);
        membership.setIsOwner(false);
        membership.setIsDefault(false);
        return membership;
    }
}
