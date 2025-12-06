package com.learning.authservice.invitation.service;

import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.dto.InvitationRequest;
import com.learning.authservice.invitation.dto.InvitationResponse;

import java.util.List;
import java.util.UUID;

public interface InvitationService {

    InvitationResponse createInvitation(String tenantId, String invitedBy, InvitationRequest request);

    List<InvitationResponse> getInvitations(String tenantId);

    void revokeInvitation(String tenantId, UUID invitationId);

    void resendInvitation(String tenantId, UUID invitationId);

    Invitation validateInvitation(String token);

    void acceptInvitation(String token, String password, String name);
}
