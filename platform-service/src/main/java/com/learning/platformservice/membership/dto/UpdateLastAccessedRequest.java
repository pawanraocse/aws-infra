package com.learning.platformservice.membership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating last accessed timestamp.
 */
public record UpdateLastAccessedRequest(

        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,

        @NotBlank(message = "Tenant ID is required") String tenantId) {
}
