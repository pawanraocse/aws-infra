package com.learning.authservice.invitation.service;

import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing user invitations.
 * Tenant isolation is handled via TenantDataSourceRouter.
 */
public interface InvitationService {

    InvitationResponse createInvitation(String invitedBy, InvitationRequest request);

    List<InvitationResponse> getInvitations();

    void revokeInvitation(UUID invitationId);

    void resendInvitation(UUID invitationId);

    Invitation validateInvitation(String token);

    void acceptInvitation(String token, String password, String name);
}
