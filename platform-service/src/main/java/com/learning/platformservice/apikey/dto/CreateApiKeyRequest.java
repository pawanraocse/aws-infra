package com.learning.platformservice.apikey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new API key.
 */
public record CreateApiKeyRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be less than 100 characters") String name,

        @Min(value = 1, message = "Expiration must be at least 1 day") @Max(value = 730, message = "Expiration cannot exceed 730 days (2 years)") int expiresInDays) {
    public CreateApiKeyRequest {
        // Default to 365 days if not specified
        if (expiresInDays <= 0) {
            expiresInDays = 365;
        }
    }
}
