package com.learning.platformservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * AWS Cognito SDK client configuration.
 * Used by SsoConfigurationService to manage identity providers.
 */
@Configuration
public class CognitoConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "cognito", name = "user-pool-id")
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(CognitoProperties props) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
