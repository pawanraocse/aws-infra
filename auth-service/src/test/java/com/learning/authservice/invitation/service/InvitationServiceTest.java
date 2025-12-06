package com.learning.authservice.invitation.service;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.repository.RoleRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.domain.InvitationStatus;
import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;
import com.learning.authservice.invitation.repository.InvitationRepository;
import com.learning.authservice.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRoleRepository userRoleRepository;

    private InvitationServiceImpl invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationServiceImpl(
                invitationRepository,
                roleRepository,
                emailService,
                userRoleRepository);
        // Inject values via reflection or constructor if possible, but here we rely on
        // default or setter if needed.
        // Since we used @Value, we might need ReflectionTestUtils or constructor
        // injection modification.
        // For this test, let's assume defaults or use ReflectionTestUtils if needed.
        // Actually, let's use ReflectionTestUtils to set the private fields.
        org.springframework.test.util.ReflectionTestUtils.setField(invitationService, "expirationHours", 48);
        org.springframework.test.util.ReflectionTestUtils.setField(invitationService, "frontendUrl",
                "http://localhost:4200");
    }

    @Test
    void createInvitation_Success() {
        // Arrange
        String tenantId = "tenant-123";
        String invitedBy = "admin-user";
        InvitationRequest request = new InvitationRequest("test@example.com", "role-user");

        Role role = new Role();
        role.setId("role-user");
        role.setScope(Role.RoleScope.TENANT);

        when(roleRepository.findById("role-user")).thenReturn(Optional.of(role));
        when(invitationRepository.findByTenantIdAndEmail(tenantId, request.getEmail())).thenReturn(Optional.empty());
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation inv = invocation.getArgument(0);
            inv.setId(UUID.randomUUID());
            inv.setCreatedAt(Instant.now());
            return inv;
        });

        // Act
        InvitationResponse response = invitationService.createInvitation(tenantId, invitedBy, request);

        // Assert
        assertNotNull(response);
        assertEquals(request.getEmail(), response.getEmail());
        assertEquals(InvitationStatus.PENDING, response.getStatus());

        verify(emailService).sendInvitationEmail(eq(request.getEmail()), anyString(), eq(tenantId));
    }

    @Test
    void createInvitation_PlatformRole_ThrowsException() {
        // Arrange
        String tenantId = "tenant-123";
        InvitationRequest request = new InvitationRequest("test@example.com", "role-super-admin");

        Role role = new Role();
        role.setId("role-super-admin");
        role.setScope(Role.RoleScope.PLATFORM);

        when(roleRepository.findById("role-super-admin")).thenReturn(Optional.of(role));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.createInvitation(tenantId, "admin", request));
    }

    @Test
    void revokeInvitation_Success() {
        // Arrange
        String tenantId = "tenant-123";
        UUID invitationId = UUID.randomUUID();
        Invitation invitation = new Invitation();
        invitation.setId(invitationId);
        invitation.setTenantId(tenantId);
        invitation.setStatus(InvitationStatus.PENDING);

        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        // Act
        invitationService.revokeInvitation(tenantId, invitationId);

        // Assert
        assertEquals(InvitationStatus.REVOKED, invitation.getStatus());
        verify(invitationRepository).save(invitation);
    }
}
