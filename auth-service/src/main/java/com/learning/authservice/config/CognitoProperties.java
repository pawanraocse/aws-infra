package com.learning.authservice.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AWS Cognito integration.
 * Values are loaded from application.yml and environment variables.
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "cognito")
@Data
public class CognitoProperties {

    /**
     * Cognito User Pool ID
     */
    private String userPoolId;

    /**
     * Cognito domain (e.g., "my-app-dev-xyz123")
     */
    private String domain;

    /**
     * AWS region where Cognito resources exist
     */
    private String region;

    /**
     * OAuth2 client ID from Spring Security configuration
     */
    private String clientId;

    /**
     * OAuth2 client secret (required for native/confidential clients)
     */
    private String clientSecret;

    /**
     * Logout redirect URL
     */
    private String logoutRedirectUrl;

    /**
     * Validate required properties after construction
     */
    @PostConstruct
    void validate() {
        StringBuilder sb = new StringBuilder();
        if (isBlank(userPoolId))
            sb.append("userPoolId ");
        if (isBlank(domain))
            sb.append("domain ");
        if (isBlank(region))
            sb.append("region ");
        if (isBlank(clientId))
            sb.append("clientId ");
        if (!sb.isEmpty()) {
            String missing = sb.toString().trim();
            String msg = "Missing required Cognito properties: " + missing;
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.info("Cognito properties validated region={} userPoolId={} domain={}", region, userPoolId, domain);
    }

    /**
     * Get the full Cognito domain URL
     */
    public String getDomainUrl() {
        return String.format("https://%s.auth.%s.amazoncognito.com", domain, region);
    }

    /**
     * Get the logout URL
     */
    public String getLogoutUrl() {
        return getDomainUrl() + "/logout";
    }

    /**
     * Check if a string is null or empty
     */
    private boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
