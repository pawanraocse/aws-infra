package com.learning.authservice.service;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.dto.AuthRequestDto;
import com.learning.authservice.dto.AuthResponseDto;
import com.learning.authservice.exception.AuthLoginException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl.login method.
 * Tests Cognito integration, success paths, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplLoginTest {

        @Mock
        private CognitoIdentityProviderClient cognitoClient;

        @Mock
        private HttpServletRequest request;

        @Mock
        private HttpServletResponse response;

        @Mock
        private WebClient platformWebClient;

        @Mock
        private UserRoleService userRoleService;

        private AuthServiceImpl authService;

        @BeforeEach
        void setUp() {
                CognitoProperties props = new CognitoProperties();
                props.setUserPoolId("us-east-1_testpool");
                props.setClientId("test-client-id");
                props.setDomain("test.auth.us-east-1.amazoncognito.com");
                props.setRegion("us-east-1");

                authService = new AuthServiceImpl(props, request, response, cognitoClient, platformWebClient,
                                userRoleService);
        }

        @Test
        @DisplayName("login returns auth response on successful Cognito authentication")
        void login_Success_ReturnsAuthResponse() {
                // Given
                AuthRequestDto loginRequest = new AuthRequestDto("user@example.com", "password123");

                AuthenticationResultType authResult = AuthenticationResultType.builder()
                                .accessToken("access-token-123")
                                .refreshToken("refresh-token-456")
                                .tokenType("Bearer")
                                .expiresIn(3600)
                                .build();

                AdminInitiateAuthResponse cognitoResponse = AdminInitiateAuthResponse.builder()
                                .authenticationResult(authResult)
                                .build();

                when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                                .thenReturn(cognitoResponse);

                // When
                AuthResponseDto result = authService.login(loginRequest);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getAccessToken()).isEqualTo("access-token-123");
                assertThat(result.getRefreshToken()).isEqualTo("refresh-token-456");
                assertThat(result.getTokenType()).isEqualTo("Bearer");
                assertThat(result.getExpiresIn()).isEqualTo(3600L);
                assertThat(result.getEmail()).isEqualTo("user@example.com");

                verify(cognitoClient).adminInitiateAuth(any(AdminInitiateAuthRequest.class));
        }

        @Test
        @DisplayName("login throws AuthLoginException with INVALID_CREDENTIALS on NotAuthorizedException")
        void login_InvalidCredentials_ThrowsAuthLoginException() {
                // Given
                AuthRequestDto loginRequest = new AuthRequestDto("user@example.com", "wrong-password");

                when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                                .thenThrow(NotAuthorizedException.builder()
                                                .message("Incorrect username or password")
                                                .build());

                // When/Then
                assertThatThrownBy(() -> authService.login(loginRequest))
                                .isInstanceOf(AuthLoginException.class)
                                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("login throws AuthLoginException with USER_NOT_FOUND on UserNotFoundException")
        void login_UserNotFound_ThrowsAuthLoginException() {
                // Given
                AuthRequestDto loginRequest = new AuthRequestDto("nonexistent@example.com", "password");

                when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                                .thenThrow(UserNotFoundException.builder()
                                                .message("User does not exist")
                                                .build());

                // When/Then
                assertThatThrownBy(() -> authService.login(loginRequest))
                                .isInstanceOf(AuthLoginException.class)
                                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
        }

        @Test
        @DisplayName("login throws AuthLoginException with LOGIN_FAILED on generic Cognito error")
        void login_CognitoError_ThrowsAuthLoginException() {
                // Given
                AuthRequestDto loginRequest = new AuthRequestDto("user@example.com", "password");

                AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                                .errorCode("ServiceError")
                                .errorMessage("Service temporarily unavailable")
                                .build();

                CognitoIdentityProviderException cognitoException = (CognitoIdentityProviderException) CognitoIdentityProviderException
                                .builder()
                                .message("Service error")
                                .awsErrorDetails(errorDetails)
                                .build();

                when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                                .thenThrow(cognitoException);

                // When/Then
                assertThatThrownBy(() -> authService.login(loginRequest))
                                .isInstanceOf(AuthLoginException.class)
                                .hasFieldOrPropertyWithValue("code", "LOGIN_FAILED");
        }

        @Test
        @DisplayName("login logs request ID for traceability")
        void login_LogsRequestId() {
                // Given
                AuthRequestDto loginRequest = new AuthRequestDto("user@example.com", "password");
                when(request.getAttribute("X-Request-Id")).thenReturn("req-123");

                AuthenticationResultType authResult = AuthenticationResultType.builder()
                                .accessToken("token")
                                .refreshToken("refresh")
                                .tokenType("Bearer")
                                .expiresIn(3600)
                                .build();

                when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                                .thenReturn(AdminInitiateAuthResponse.builder()
                                                .authenticationResult(authResult)
                                                .build());

                // When
                authService.login(loginRequest);

                // Then - verify request ID is accessed for logging
                verify(request).getAttribute("X-Request-Id");
        }
}
