package com.learning.authservice.authorization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for ACL entry operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclEntryDto {
    private UUID id;
    private UUID resourceId;
    private String resourceType; // PROJECT, FOLDER, FILE
    private String principalType; // USER, GROUP, PUBLIC
    private String principalId;
    private String roleBundle; // VIEWER, CONTRIBUTOR, EDITOR, MANAGER
    private String grantedBy;
    private Instant grantedAt;
    private Instant expiresAt;
}
