package com.learning.platformservice.apikey.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for newly created API key.
 * Contains the raw key - shown ONLY ONCE at creation.
 */
public record CreateApiKeyResponse(
        UUID id,
        String name,
        String key, // ⚠️ ONLY TIME THE RAW KEY IS RETURNED
        Instant expiresAt,
        Instant createdAt) {
}
