package com.learning.authservice.authorization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for granting access to a resource.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantAccessRequest {
    private UUID resourceId;
    private String resourceType; // PROJECT, FOLDER, FILE
    private String principalType; // USER, GROUP
    private String principalId; // User ID or Group ID
    private String roleBundle; // VIEWER, CONTRIBUTOR, EDITOR, MANAGER
    private Instant expiresAt; // Optional expiration
}
