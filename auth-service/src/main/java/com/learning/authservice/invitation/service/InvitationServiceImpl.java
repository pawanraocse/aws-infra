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
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing user invitations.
 * Tenant isolation is handled via TenantDataSourceRouter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final EmailService emailService;
    private final UserRoleRepository userRoleRepository;

    @Value("${app.invitation.expiration-hours:48}")
    private int expirationHours;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    @Transactional
    public InvitationResponse createInvitation(String invitedBy, InvitationRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Creating invitation for email={} by={}", request.getEmail(), invitedBy);

        // 1. Validate Role
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.getRoleId()));

        if (role.getScope() == Role.RoleScope.PLATFORM) {
            throw new IllegalArgumentException("Cannot invite users with PLATFORM scope roles");
        }

        // 2. Check for existing pending invitation
        invitationRepository.findByEmail(request.getEmail())
                .ifPresent(existing -> {
                    if (existing.getStatus() == InvitationStatus.PENDING) {
                        throw new IllegalStateException("Active invitation already exists for this email");
                    }
                });

        // 3. Generate Token
        String token = generateSecureToken();

        // 4. Create Invitation
        Invitation invitation = Invitation.builder()
                .email(request.getEmail())
                .roleId(request.getRoleId())
                .token(token)
                .status(InvitationStatus.PENDING)
                .invitedBy(invitedBy)
                .expiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS))
                .build();

        invitation = invitationRepository.save(invitation);

        // 5. Send Email
        String inviteLink = frontendUrl + "/auth/join?token=" + token;
        emailService.sendInvitationEmail(request.getEmail(), inviteLink, tenantId);

        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> getInvitations() {
        return invitationRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeInvitation(UUID invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Cannot revoke invitation with status: " + invitation.getStatus());
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
        log.info("Revoked invitation id={}", invitationId);
    }

    @Override
    @Transactional
    public void resendInvitation(UUID invitationId) {
        String tenantId = TenantContext.getCurrentTenant();
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Cannot resend invitation with status: " + invitation.getStatus());
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setExpiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS));
            invitationRepository.save(invitation);
        }

        String inviteLink = frontendUrl + "/auth/join?token=" + invitation.getToken();
        emailService.sendInvitationEmail(invitation.getEmail(), inviteLink, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Invitation validateInvitation(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is no longer valid (Status: " + invitation.getStatus() + ")");
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalStateException("Invitation has expired");
        }

        return invitation;
    }

    @Override
    @Transactional
    public void acceptInvitation(String token, String password, String name) {
        Invitation invitation = validateInvitation(token);

        log.info("Accepting invitation for email={}", invitation.getEmail());

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private InvitationResponse mapToResponse(Invitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .roleId(invitation.getRoleId())
                .status(invitation.getStatus())
                .invitedBy(invitation.getInvitedBy())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
