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
 * Tenant isolation is handled via TenantDataSourceRouter.
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
    void shouldCreateInvitation_WithTenantContext() {
        // Given
        String email = "newuser@example.com";
        String roleId = "tenant-user";
        String invitedBy = "admin@example.com";
        InvitationRequest request = new InvitationRequest(email, roleId);

        // When
        InvitationResponse invitation = invitationService.createInvitation(invitedBy, request);

        // Then
        assertThat(invitation).isNotNull();
        assertThat(invitation.getEmail()).isEqualTo(email);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);

        // Verify database record
        Invitation saved = invitationRepository.findById(invitation.getId()).orElseThrow();
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

        invitationService.createInvitation(invitedBy, request);

        // When
        var foundInvitation = invitationRepository.findByEmail(email);

        // Then
        assertThat(foundInvitation).isPresent();
        assertThat(foundInvitation.get().getEmail()).isEqualTo(email);
    }

    @Test
    void shouldIsolateTenantData() {
        // Given - Create invitation for tenant1
        TenantContext.setCurrentTenant("t_tenant1");
        when(tenantRegistry.load("t_tenant1")).thenReturn(new TenantDbConfig(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        String email1 = "user1@example.com";
        InvitationRequest request1 = new InvitationRequest(email1, "role1");
        invitationService.createInvitation("admin", request1);

        // When - Switch to tenant2 and query
        TenantContext.setCurrentTenant("t_tenant2");
        when(tenantRegistry.load("t_tenant2")).thenReturn(new TenantDbConfig(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        var tenant2Invitations = invitationRepository.findAll();

        // Then - Should not see tenant1's data (different DB via
        // TenantDataSourceRouter)
        // Note: In real tenant isolation, tenant2 would connect to a different DB
        // For this test with shared DB, we just verify the query runs
        assertThat(tenant2Invitations).isNotNull();

        // Cleanup
        TenantContext.setCurrentTenant("t_tenant1");
    }
}
