package com.learning.authservice.sso.dto;

import lombok.Builder;

import java.util.List;

/**
 * Result of SSO connection test.
 */
@Builder
public record SsoTestResult(
        boolean success,
        String message,
        String providerName,
        String entityId,
        List<String> availableAttributes,
        long responseTimeMs) {
}
