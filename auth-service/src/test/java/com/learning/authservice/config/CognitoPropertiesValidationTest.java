package com.learning.authservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CognitoProperties validation (NT-17 + NT-19).
 */
class CognitoPropertiesValidationTest {

    @Test
    @DisplayName("fails fast when required properties missing")
    void failsFastWhenMissingProperties() {
        CognitoProperties props = new CognitoProperties();
        // leave all blank to trigger validation exception
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required Cognito properties");
    }

    @Test
    @DisplayName("computes domain and logout URLs when valid")
    void computesDerivedUrls() {
        CognitoProperties props = new CognitoProperties();
        props.setUserPoolId("pool123");
        props.setDomain("my-app-dev-xyz");
        props.setRegion("us-east-1");
        props.setClientId("client-abc");
        props.setLogoutRedirectUrl("http://localhost:8081/auth/logged-out");
        // Should not throw
        props.validate();
        assertThat(props.getDomainUrl()).isEqualTo("https://my-app-dev-xyz.auth.us-east-1.amazoncognito.com");
        assertThat(props.getLogoutUrl()).isEqualTo("https://my-app-dev-xyz.auth.us-east-1.amazoncognito.com/logout");
    }
}

