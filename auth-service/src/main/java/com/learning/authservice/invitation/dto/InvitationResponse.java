package com.learning.authservice.invitation.dto;

import com.learning.authservice.invitation.domain.InvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {

    private UUID id;
    private String email;
    private String roleId;
    private InvitationStatus status;
    private String invitedBy;
    private Instant expiresAt;
    private Instant createdAt;
}
