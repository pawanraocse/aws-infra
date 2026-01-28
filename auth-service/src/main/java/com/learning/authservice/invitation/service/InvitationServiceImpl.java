package com.learning.authservice.invitation.service;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.repository.RoleRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.domain.InvitationStatus;
import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;
import com.learning.authservice.invitation.repository.InvitationRepository;
import com.learning.authservice.service.EmailService;
import com.learning.authservice.signup.CognitoUserRegistrar;
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
    private final CognitoUserRegistrar cognitoUserRegistrar;
    private final UserRoleService userRoleService;
    private final CognitoProperties cognitoProperties;
    private final software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient cognitoClient;

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

        // 2. Check for existing pending invitation (use findByEmailAndStatus to avoid
        // duplicate result issues)
        invitationRepository.findByEmailAndStatus(request.getEmail(), InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Active invitation already exists for this email");
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
        String inviteLink = buildInvitationLink(token, tenantId);
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

        String inviteLink = buildInvitationLink(invitation.getToken(), tenantId);
        emailService.sendInvitationEmail(invitation.getEmail(), inviteLink, tenantId);
    }

    /**
     * Builds invitation link with Angular hash routing and tenant parameter.
     * Uses /#/ for Angular hash-based routing.
     * Includes tenant param for validation endpoint to know which DB to query.
     */
    private String buildInvitationLink(String token, String tenantId) {
        return frontendUrl + "/#/auth/join?token=" + token + "&tenant=" + tenantId;
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
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Accepting invitation for email={} tenant={} role={}",
                invitation.getEmail(), tenantId, invitation.getRoleId());

        // 1. Create Cognito user (or skip if exists)
        CognitoUserRegistrar.UserRegistration result = cognitoUserRegistrar.registerIfNotExists(
                invitation.getEmail(),
                password,
                name,
                tenantId,
                invitation.getRoleId());

        // 2. Auto-confirm the user (invitation link proves email ownership)
        // If user was just created (not already existing), auto-confirm
        if (!result.alreadyExisted()) {
            autoConfirmUser(invitation.getEmail());
        }

        // 3. Get user's sub from Cognito and assign role in tenant DB
        String userId = getUserSubFromCognito(invitation.getEmail());
        userRoleService.assignRole(userId, invitation.getRoleId(), "invitation");
        log.info("Role assigned: userId={} role={}", userId, invitation.getRoleId());

        // 4. Add user to platform DB for workspace discovery
        addPlatformMembership(invitation.getEmail(), userId, tenantId, invitation.getRoleId(),
                invitation.getInvitedBy());

        // 5. Mark invitation as accepted
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        log.info("Invitation accepted: email={} cognitoResult={}", invitation.getEmail(), result);
    }

    /**
     * Add user to platform DB so they can discover this workspace during login.
     */
    private void addPlatformMembership(String email, String cognitoUserId, String tenantId, String roleId,
            String invitedBy) {
        try {
            String platformServiceUrl = "http://platform-service:8083/platform/internal/memberships";

            var requestBody = java.util.Map.of(
                    "email", email,
                    "cognitoUserId", cognitoUserId,
                    "tenantId", tenantId,
                    "roleHint", roleId,
                    "isOwner", false,
                    "isDefault", false,
                    "invitedBy", invitedBy != null ? invitedBy : "");

            var restClient = org.springframework.web.client.RestClient.create();
            restClient.post()
                    .uri(platformServiceUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Platform membership created: email={} tenantId={}", email, tenantId);
        } catch (Exception e) {
            log.error("Failed to create platform membership: email={} tenantId={} error={}",
                    email, tenantId, e.getMessage());
            // Don't fail the invitation - admin can fix manually
        }
    }

    /**
     * Auto-confirm a user in Cognito since invitation link proves email ownership.
     */
    private void autoConfirmUser(String email) {
        try {
            var confirmRequest = software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpRequest
                    .builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(email)
                    .build();
            cognitoClient.adminConfirmSignUp(confirmRequest);

            // Also verify email attribute
            var updateRequest = software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest
                    .builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(email)
                    .userAttributes(
                            software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                    .name("email_verified")
                                    .value("true")
                                    .build())
                    .build();
            cognitoClient.adminUpdateUserAttributes(updateRequest);

            log.info("User auto-confirmed and email verified: {}", email);
        } catch (Exception e) {
            log.error("Failed to auto-confirm user: {} error={}", email, e.getMessage());
            throw new RuntimeException("Failed to confirm user: " + e.getMessage());
        }
    }

    private String getUserSubFromCognito(String email) {
        var getUserRequest = software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .username(email)
                .build();

        var userResponse = cognitoClient.adminGetUser(getUserRequest);
        return userResponse.userAttributes().stream()
                .filter(attr -> "sub".equals(attr.name()))
                .map(software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType::value)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to get user sub from Cognito"));
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
