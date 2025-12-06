package com.learning.authservice.invitation.service;

import com.learning.authservice.config.AbstractIntegrationTest;
import com.learning.authservice.config.TestDataSourceConfig;
import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.domain.InvitationStatus;
import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;
import com.learning.authservice.invitation.repository.InvitationRepository;
import com.learning.authservice.tenant.AuthServiceTenantRegistry;
import com.learning.common.dto.TenantDbConfig;
import com.learning.common.infra.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for InvitationService using Testcontainers.
 * Tests the full stack including database interactions and tenant context
 * handling.
 * 
 * Extends AbstractIntegrationTest for shared Testcontainers setup.
 */
@Import(TestDataSourceConfig.class)
@Testcontainers
@ActiveProfiles("test")
@Disabled("Blocked by context initialization timing issue - see integration_test_blocker.md")
class InvitationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @MockBean
    private AuthServiceTenantRegistry tenantRegistry;

    private static final String TEST_TENANT_ID = "t_test_tenant";

    @BeforeEach
    void setUp() {
        // Set tenant context for the test
        TenantContext.setCurrentTenant(TEST_TENANT_ID);

        // Mock tenant registry to return Testcontainer DB config
        TenantDbConfig mockConfig = new TenantDbConfig(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
        when(tenantRegistry.load(TEST_TENANT_ID)).thenReturn(mockConfig);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // Clean up test data
        try {
            invitationRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void shouldCreateInvitation_WithTenantId() {
        // Given
        String email = "newuser@example.com";
        String roleId = "tenant-user";
        String invitedBy = "admin@example.com";
        InvitationRequest request = new InvitationRequest(email, roleId);

        // When
        InvitationResponse invitation = invitationService.createInvitation(TEST_TENANT_ID, invitedBy, request);

        // Then
        assertThat(invitation).isNotNull();
        assertThat(invitation.getEmail()).isEqualTo(email);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);

        // Verify database constraint - tenant_id should NOT be NULL
        Invitation saved = invitationRepository.findById(invitation.getId()).orElseThrow();
        assertThat(saved.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(saved.getRoleId()).isEqualTo(roleId);
        assertThat(saved.getInvitedBy()).isEqualTo(invitedBy);
    }

    @Test
    void shouldFindInvitationByEmail() {
        // Given
        String email = "test@example.com";
        String roleId = "tenant-admin";
        String invitedBy = "super-admin@example.com";
        InvitationRequest request = new InvitationRequest(email, roleId);

        invitationService.createInvitation(TEST_TENANT_ID, invitedBy, request);

        // When
        var foundInvitation = invitationRepository.findByTenantIdAndEmail(TEST_TENANT_ID, email);

        // Then
        assertThat(foundInvitation).isPresent();
        assertThat(foundInvitation.get().getEmail()).isEqualTo(email);
        assertThat(foundInvitation.get().getTenantId()).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    void shouldIsolateTenantData() {
        // Given - Create invitation for tenant1
        TenantContext.setCurrentTenant("t_tenant1");
        when(tenantRegistry.load("t_tenant1")).thenReturn(new TenantDbConfig(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        String email1 = "user1@example.com";
        InvitationRequest request1 = new InvitationRequest(email1, "role1");
        invitationService.createInvitation("t_tenant1", "admin", request1);

        // When - Switch to tenant2 and query
        TenantContext.setCurrentTenant("t_tenant2");
        when(tenantRegistry.load("t_tenant2")).thenReturn(new TenantDbConfig(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        var tenant2Invitations = invitationRepository.findByTenantId("t_tenant2");

        // Then - Should not see tenant1's data
        assertThat(tenant2Invitations).isEmpty();

        // Cleanup
        TenantContext.setCurrentTenant("t_tenant1");
    }
}
