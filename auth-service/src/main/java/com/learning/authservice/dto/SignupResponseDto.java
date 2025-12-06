package com.learning.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for signup operations.
 * Contains information about the signup result and verification status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponseDto {

    /**
     * User's email address
     */
    private String email;

    /**
     * Whether the user is confirmed (email verified)
     * false = needs email verification
     * true = already confirmed (shouldn't happen with new flow)
     */
    private boolean userConfirmed;

    /**
     * Cognito user sub (unique identifier)
     */
    private String userSub;

    /**
     * Message to display to user
     */
    private String message;

    /**
     * Tenant ID (if already provisioned)
     */
    private String tenantId;
}
