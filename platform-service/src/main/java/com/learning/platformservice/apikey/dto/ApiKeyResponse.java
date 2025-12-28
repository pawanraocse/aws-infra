package com.learning.platformservice.apikey.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for API key listing (excludes sensitive data).
 */
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix, // e.g., "sk_live_a1b2" for identification
        String createdByEmail,
        Integer rateLimitPerMinute,
        Instant expiresAt,
        Instant lastUsedAt,
        Long usageCount,
        Instant createdAt,
        String status) {
}
