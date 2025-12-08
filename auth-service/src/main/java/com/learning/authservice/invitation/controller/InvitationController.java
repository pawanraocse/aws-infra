package com.learning.authservice.invitation.controller;

import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;
import com.learning.authservice.invitation.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import com.learning.common.infra.security.RequirePermission;

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
}
