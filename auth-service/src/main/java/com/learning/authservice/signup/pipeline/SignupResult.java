package com.learning.authservice.signup.pipeline;

/**
 * Result of signup pipeline execution.
 */
public record SignupResult(
        boolean success,
        String message,
        String tenantId,
        boolean requiresEmailVerification,
        String failedActionName,
        Throwable error) {
    public static SignupResult success(String message, String tenantId, boolean requiresEmailVerification) {
        return new SignupResult(true, message, tenantId, requiresEmailVerification, null, null);
    }

    public static SignupResult failure(String message, String failedActionName, Throwable error) {
        return new SignupResult(false, message, null, false, failedActionName, error);
    }
}
