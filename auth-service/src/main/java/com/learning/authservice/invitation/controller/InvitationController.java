package com.learning.authservice.invitation.controller;

import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;
import com.learning.authservice.invitation.service.InvitationService;
import com.learning.common.infra.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for managing invitations.
 * Tenant context is implicit via TenantDataSourceRouter.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Slf4j
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @RequirePermission(resource = "user", action = "invite")
    public ResponseEntity<InvitationResponse> createInvitation(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody InvitationRequest request) {

        InvitationResponse response = invitationService.createInvitation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @RequirePermission(resource = "user", action = "read")
    public ResponseEntity<List<InvitationResponse>> getInvitations() {
        return ResponseEntity.ok(invitationService.getInvitations());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(resource = "user", action = "invite")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID id) {
        invitationService.revokeInvitation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resend")
    @RequirePermission(resource = "user", action = "invite")
    public ResponseEntity<Void> resendInvitation(@PathVariable UUID id) {
        invitationService.resendInvitation(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Validate an invitation token.
     * Public endpoint - called before user has an account.
     * Requires tenant parameter to route to correct database.
     */
    @GetMapping("/validate")
    public ResponseEntity<InvitationValidationResponse> validateInvitation(
            @RequestParam String token,
            @RequestParam String tenant) {
        // Set tenant context for database routing
        com.learning.common.infra.tenant.TenantContext.setCurrentTenant(tenant);
        try {
            var invitation = invitationService.validateInvitation(token);
            return ResponseEntity.ok(new InvitationValidationResponse(
                    invitation.getEmail(),
                    invitation.getRoleId(),
                    tenant,
                    true));
        } finally {
            com.learning.common.infra.tenant.TenantContext.clear();
        }
    }

    /**
     * Accept an invitation and create user account.
     * Public endpoint - called during join flow.
     * Requires tenant parameter to route to correct database.
     */
    @PostMapping("/accept")
    public ResponseEntity<Void> acceptInvitation(@RequestBody AcceptInvitationRequest request) {
        // Set tenant context for database routing
        com.learning.common.infra.tenant.TenantContext.setCurrentTenant(request.tenant());
        try {
            invitationService.acceptInvitation(request.token(), request.password(), request.name());
            return ResponseEntity.ok().build();
        } finally {
            com.learning.common.infra.tenant.TenantContext.clear();
        }
    }

    public record InvitationValidationResponse(String email, String roleId, String tenant, boolean valid) {
    }

    public record AcceptInvitationRequest(String token, String password, String name, String tenant) {
    }
}
