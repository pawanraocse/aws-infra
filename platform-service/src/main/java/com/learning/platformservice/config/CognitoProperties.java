package com.learning.platformservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cognito configuration properties for platform-service.
 * Used for SSO identity provider management via AWS SDK.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cognito")
public class CognitoProperties {

    /**
     * Cognito User Pool ID.
     */
    private String userPoolId;

    /**
     * Cognito App Client ID.
     */
    private String clientId;

    /**
     * Cognito domain (without https://).
     */
    private String domain;

    /**
     * AWS region where Cognito is deployed.
     */
    private String region = "us-east-1";
}
