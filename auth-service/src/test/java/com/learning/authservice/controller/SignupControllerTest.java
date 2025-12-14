package com.learning.authservice.controller;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.signup.SignupService;
import com.learning.common.dto.OrganizationSignupRequest;
import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.SignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignupController.
 * Tests that the controller correctly delegates to SignupService
 * and maps responses appropriately.
 */
@ExtendWith(MockitoExtension.class)
class SignupControllerTest {

    @Mock
    private SignupService signupService;

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private CognitoProperties cognitoProperties;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private com.learning.authservice.tenant.TenantLookupService tenantLookupService;

    private SignupController signupController;

    @BeforeEach
    void setUp() {
        signupController = new SignupController(signupService, cognitoClient, cognitoProperties, userRoleService,
                tenantLookupService);
    }

    @Test
    @DisplayName("Personal signup success - returns CREATED with unconfirmed status")
    void signupPersonal_Success_Unconfirmed() {
        // Arrange
        PersonalSignupRequest request = new PersonalSignupRequest("test@gmail.com", "password123", "Test User");
        SignupResponse serviceResponse = SignupResponse.success(
                "Signup complete. Please verify your email.",
                "user-test-12345",
                false);
        when(signupService.signup(any())).thenReturn(serviceResponse);

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupPersonal(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().tenantId()).isEqualTo("user-test-12345");
        assertThat(response.getBody().userConfirmed()).isFalse();

        verify(signupService).signup(any());
    }

    @Test
    @DisplayName("Organization signup success - returns CREATED with unconfirmed (email verify required)")
    void signupOrganization_Success_RequiresEmailVerification() {
        // Arrange
        OrganizationSignupRequest request = new OrganizationSignupRequest(
                "Acme Corp", "admin@acme.com", "Admin User", "password123", "STANDARD");

        SignupResponse serviceResponse = SignupResponse.success(
                "Signup complete. Please verify your email.",
                "acme-corp",
                false // Now requires email verification (unified flow)
        );
        when(signupService.signup(any())).thenReturn(serviceResponse);

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupOrganization(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().tenantId()).isEqualTo("acme-corp");
        assertThat(response.getBody().userConfirmed()).isFalse(); // Email verification required

        verify(signupService).signup(any());
    }

    @Test
    @DisplayName("Personal signup fails when user already exists")
    void signupPersonal_UserExists_ReturnsBadRequest() {
        // Arrange
        PersonalSignupRequest request = new PersonalSignupRequest("existing@gmail.com", "password123", "Test User");
        SignupResponse failureResponse = SignupResponse.failure("User with email existing@gmail.com already exists");
        when(signupService.signup(any())).thenReturn(failureResponse);

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupPersonal(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("already exists");
    }

    @Test
    @DisplayName("Organization signup fails - returns BAD_REQUEST")
    void signupOrganization_Failure_ReturnsBadRequest() {
        // Arrange
        OrganizationSignupRequest request = new OrganizationSignupRequest(
                "Acme Corp", "admin@acme.com", "Admin User", "password123", "STANDARD");

        SignupResponse failureResponse = SignupResponse.failure("Tenant provisioning failed");
        when(signupService.signup(any())).thenReturn(failureResponse);

        // Act
        ResponseEntity<SignupResponse> response = signupController.signupOrganization(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("provisioning failed");
    }
}
