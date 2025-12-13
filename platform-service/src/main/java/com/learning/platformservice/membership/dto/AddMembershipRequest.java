package com.learning.platformservice.membership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a new user-tenant membership.
 * Used when users sign up, are invited, or join organizations.
 */
public record AddMembershipRequest(

        /**
         * Email of the user to add.
         */
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,

        /**
         * Cognito user ID (optional, can be set later).
         */
        String cognitoUserId,

        /**
         * Tenant to add the user to.
         */
        @NotBlank(message = "Tenant ID is required") String tenantId,

        /**
         * Role hint for display (owner, admin, member, guest).
         */
        String roleHint,

        /**
         * Whether this user owns the tenant.
         */
        Boolean isOwner,

        /**
         * Whether this should be the user's default workspace.
         */
        Boolean isDefault,

        /**
         * Email of the user who invited this member (null for owners).
         */
        String invitedBy) {
}
