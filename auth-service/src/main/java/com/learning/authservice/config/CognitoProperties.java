package com.learning.authservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AWS Cognito integration.
 * Values are loaded from application.yml and environment variables.
 */
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
     * Logout redirect URL
     */
    private String logoutRedirectUrl;
    
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
}

