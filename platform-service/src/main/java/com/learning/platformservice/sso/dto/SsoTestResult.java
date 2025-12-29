package com.learning.platformservice.sso.dto;

import lombok.Builder;

/**
 * Result of SSO connection test.
 */
@Builder
public record SsoTestResult(
        boolean success,
        String message,
        String providerName,
        String entityId,
        java.util.List<String> availableAttributes,
        long responseTimeMs) {
}
