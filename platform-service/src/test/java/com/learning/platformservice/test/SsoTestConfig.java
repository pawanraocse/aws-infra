package com.learning.platformservice.test;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Test configuration providing mock beans for SSO testing.
 * This replaces the real CognitoIdentityProviderClient with a mock.
 */
@TestConfiguration
public class SsoTestConfig {

    @Bean
    @Primary
    public CognitoIdentityProviderClient mockCognitoIdentityProviderClient() {
        return Mockito.mock(CognitoIdentityProviderClient.class);
    }
}
