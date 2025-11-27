package com.learning.authservice.controller;

import com.learning.authservice.config.CognitoProperties;
import com.learning.common.dto.OrganizationSignupRequest;
import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.common.dto.SignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupControllerTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private CognitoProperties cognitoProperties;

    @Mock
    private WebClient platformWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private SignupController signupController;

    @BeforeEach
    void setUp() {
        signupController = new SignupController(cognitoClient, cognitoProperties, platformWebClient);
        lenient().when(cognitoProperties.getUserPoolId()).thenReturn("us-east-1_xxxxxx");
    }

    private void mockWebClientSuccess() {
        when(platformWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ProvisionTenantRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());
        // when(responseSpec.timeout(any(Duration.class))).thenReturn(Mono.empty()); //
        // Removed invalid mock
        // Actually, the chain is .bodyToMono(Void.class).timeout(...).block()
        // So we need to mock the Mono returned by bodyToMono

        // Let's adjust the mocking strategy for the reactive chain
        Mono<Void> voidMono = Mono.empty();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(voidMono);
        // Note: timeout() is called on the Mono, not on responseSpec.
        // But since we can't easily mock final classes or Mono methods without
        // PowerMock,
        // and the controller calls .timeout() on the Mono, we might need to rely on the
        // fact that Mono.empty().timeout() returns a Mono.
        // However, the controller code is:
        // .bodyToMono(Void.class)
        // .timeout(Duration.ofSeconds(60))
        // .block();

        // Since we return Mono.empty(), calling .timeout() on it works fine in real
        // code.
        // We don't need to mock .timeout() if we return a real Mono.
    }

    @Test
    @DisplayName("Personal signup success")
    void signupPersonal_Success() {
        // Arrange
        PersonalSignupRequest request = new PersonalSignupRequest("test@gmail.com", "password123", "Test User");

        mockWebClientSuccess();

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupPersonal(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().tenantId()).startsWith("user_test");

        verify(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));
        verify(cognitoClient).adminSetUserPassword(any(java.util.function.Consumer.class));
    }

    @Test
    @DisplayName("Organization signup success")
    void signupOrganization_Success() {
        // Arrange
        OrganizationSignupRequest request = new OrganizationSignupRequest(
                "Acme Corp", "admin@acme.com", "Admin User", "password123", "STANDARD");

        mockWebClientSuccess();

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupOrganization(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().tenantId()).isEqualTo("acme-corp");

        verify(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));
        verify(cognitoClient).adminSetUserPassword(any(java.util.function.Consumer.class));
    }

    @Test
    @DisplayName("Personal signup fails when user already exists")
    void signupPersonal_UserExists() {
        // Arrange
        PersonalSignupRequest request = new PersonalSignupRequest("existing@gmail.com", "password123", "Test User");

        mockWebClientSuccess();

        doThrow(UsernameExistsException.builder().message("User exists").build())
                .when(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupPersonal(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("User with email existing@gmail.com already exists");
    }

    @Test
    @DisplayName("Organization signup fails with non-corporate email")
    void signupOrganization_InvalidEmail() {
        // Arrange
        OrganizationSignupRequest request = new OrganizationSignupRequest(
                "Acme Corp", "admin@gmail.com", "Admin User", "password123", "STANDARD");

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupOrganization(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Please use a corporate email address");

        verifyNoInteractions(platformWebClient);
        verifyNoInteractions(cognitoClient);
    }
}
