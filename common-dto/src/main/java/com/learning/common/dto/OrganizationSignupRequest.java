package com.learning.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for B2B organization signup.
 * Used by auth-service signup endpoint.
 */
public record OrganizationSignupRequest(
        @NotBlank(message = "Company name is required") String companyName,

        @Email(message = "Invalid email format") @NotBlank(message = "Admin email is required") String adminEmail,

        @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,

        @NotBlank(message = "Admin name is required") String adminName,

        @Pattern(regexp = "STANDARD|PREMIUM|ENTERPRISE", message = "Tier must be STANDARD, PREMIUM, or ENTERPRISE") String tier) {
}
