package com.learning.common.dto;

/**
 * Response DTO for signup operations (B2C and B2B).
 * Returned by auth-service signup endpoints.
 */
public record SignupResponse(
        boolean success,
        String message,
        String tenantId,
        boolean userConfirmed) {
    /**
     * Factory method for successful signup
     */
    public static SignupResponse success(String message, String tenantId, boolean userConfirmed) {
        return new SignupResponse(true, message, tenantId, userConfirmed);
    }

    /**
     * Factory method for failed signup
     */
    public static SignupResponse failure(String message) {
        return new SignupResponse(false, message, null, false);
    }
}
