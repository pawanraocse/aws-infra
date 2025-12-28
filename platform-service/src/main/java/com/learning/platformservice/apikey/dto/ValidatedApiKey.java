package com.learning.platformservice.apikey.dto;

/**
 * Internal DTO for validated API key info (used by gateway).
 */
public record ValidatedApiKey(
        String keyId,
        String tenantId,
        String userId, // Creator's user ID for permission inheritance
        String userEmail,
        Integer rateLimitPerMinute,
        boolean valid,
        String errorCode // API_KEY_INVALID, API_KEY_EXPIRED, API_KEY_REVOKED, null if valid
) {
    public static ValidatedApiKey invalid(String errorCode) {
        return new ValidatedApiKey(null, null, null, null, null, false, errorCode);
    }

    public static ValidatedApiKey valid(String keyId, String tenantId, String userId,
            String userEmail, Integer rateLimit) {
        return new ValidatedApiKey(keyId, tenantId, userId, userEmail, rateLimit, true, null);
    }
}
