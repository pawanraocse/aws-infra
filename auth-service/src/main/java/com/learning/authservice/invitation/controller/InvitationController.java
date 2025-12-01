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
import com.learning.common.infra.tenant.TenantContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Slf4j
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @RequirePermission(resource = "user", action = "invite")
    public ResponseEntity<InvitationResponse> createInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvitationRequest request) {

        String tenantId = TenantContext.getCurrentTenant();
        String userId = jwt.getSubject();

        InvitationResponse response = invitationService.createInvitation(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @RequirePermission(resource = "user", action = "read")
    public ResponseEntity<List<InvitationResponse>> getInvitations() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(invitationService.getInvitations(tenantId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(resource = "user", action = "invite") // Revoking is part of invite management
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        invitationService.revokeInvitation(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
