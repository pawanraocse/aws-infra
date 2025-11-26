package com.learning.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for B2C personal account signup.
 * Used by auth-service signup endpoint.
 */
public record PersonalSignupRequest(
        @Email(message = "Invalid email format") @NotBlank(message = "Email is required") String email,

        @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,

        @NotBlank(message = "Name is required") String name) {
}
