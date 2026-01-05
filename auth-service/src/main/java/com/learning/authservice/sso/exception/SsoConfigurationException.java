package com.learning.authservice.sso.exception;

/**
 * Exception thrown when SSO configuration operations fail.
 */
public class SsoConfigurationException extends RuntimeException {

    public SsoConfigurationException(String message) {
        super(message);
    }

    public SsoConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static SsoConfigurationException notConfigured(String tenantId) {
        return new SsoConfigurationException("SSO not configured for tenant: " + tenantId);
    }

    public static SsoConfigurationException invalidConfiguration(String reason) {
        return new SsoConfigurationException("Invalid SSO configuration: " + reason);
    }

    public static SsoConfigurationException providerError(String provider, Throwable cause) {
        return new SsoConfigurationException("Failed to configure Cognito provider: " + provider, cause);
    }
}
