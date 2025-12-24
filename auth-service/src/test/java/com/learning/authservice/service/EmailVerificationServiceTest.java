package com.learning.authservice.service;

import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.exception.AuthSignupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeDeliveryDetailsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

        @Mock
        private CognitoIdentityProviderClient cognitoClient;

        @Mock
        private CognitoProperties cognitoProperties;

        private EmailVerificationService service;

        @BeforeEach
        void setUp() {
                service = new EmailVerificationService(cognitoClient, cognitoProperties);
                when(cognitoProperties.getClientId()).thenReturn("test-client-id");
                when(cognitoProperties.getClientSecret()).thenReturn("test-secret");
        }

        @Test
        @DisplayName("resendVerificationCode - success")
        void resendVerificationCode_success() {
                // Given
                String email = "user@example.com";
                CodeDeliveryDetailsType deliveryDetails = CodeDeliveryDetailsType.builder()
                                .deliveryMedium(DeliveryMediumType.EMAIL)
                                .destination(email)
                                .build();
                ResendConfirmationCodeResponse response = ResendConfirmationCodeResponse.builder()
                                .codeDeliveryDetails(deliveryDetails)
                                .build();
                when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                                .thenReturn(response);

                // When
                assertThatCode(() -> service.resendVerificationCode(email))
                                .doesNotThrowAnyException();

                // Then
                ArgumentCaptor<ResendConfirmationCodeRequest> captor = ArgumentCaptor
                                .forClass(ResendConfirmationCodeRequest.class);
                verify(cognitoClient).resendConfirmationCode(captor.capture());
                assertThat(captor.getValue().username()).isEqualTo(email);
                assertThat(captor.getValue().secretHash()).isNotBlank();
        }

        @Test
        @DisplayName("resendVerificationCode - user not found")
        void resendVerificationCode_userNotFound() {
                // Given
                String email = "nonexistent@example.com";
                when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                                .thenThrow(UserNotFoundException.builder().message("User not found").build());

                // When/Then
                assertThatThrownBy(() -> service.resendVerificationCode(email))
                                .isInstanceOf(AuthSignupException.class)
                                .extracting("code")
                                .isEqualTo("USER_NOT_FOUND");
        }

        @Test
        @DisplayName("resendVerificationCode - user already confirmed")
        void resendVerificationCode_alreadyConfirmed() {
                // Given
                String email = "confirmed@example.com";
                when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                                .thenThrow(InvalidParameterException.builder().message("Already confirmed").build());

                // When/Then
                assertThatThrownBy(() -> service.resendVerificationCode(email))
                                .isInstanceOf(AuthSignupException.class)
                                .extracting("code")
                                .isEqualTo("ALREADY_CONFIRMED");
        }

        @Test
        @DisplayName("resendVerificationCode - rate limit exceeded")
        void resendVerificationCode_rateLimitExceeded() {
                // Given
                String email = "user@example.com";
                when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                                .thenThrow(LimitExceededException.builder().message("Rate limit").build());

                // When/Then
                assertThatThrownBy(() -> service.resendVerificationCode(email))
                                .isInstanceOf(AuthSignupException.class)
                                .extracting("code")
                                .isEqualTo("RATE_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("confirmSignup - success")
        void confirmSignup_success() {
                // Given
                String email = "user@example.com";
                String code = "123456";
                when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                                .thenReturn(ConfirmSignUpResponse.builder().build());

                // When
                assertThatCode(() -> service.confirmSignup(email, code))
                                .doesNotThrowAnyException();

                // Then
                ArgumentCaptor<ConfirmSignUpRequest> captor = ArgumentCaptor.forClass(ConfirmSignUpRequest.class);
                verify(cognitoClient).confirmSignUp(captor.capture());
                assertThat(captor.getValue().username()).isEqualTo(email);
                assertThat(captor.getValue().confirmationCode()).isEqualTo(code);
                assertThat(captor.getValue().secretHash()).isNotBlank();
        }

        @Test
        @DisplayName("confirmSignup - invalid code")
        void confirmSignup_invalidCode() {
                // Given
                String email = "user@example.com";
                String code = "000000";
                when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                                .thenThrow(CodeMismatchException.builder().message("Invalid code").build());

                // When/Then
                assertThatThrownBy(() -> service.confirmSignup(email, code))
                                .isInstanceOf(AuthSignupException.class)
                                .extracting("code")
                                .isEqualTo("INVALID_CODE");
        }

        @Test
        @DisplayName("confirmSignup - expired code")
        void confirmSignup_expiredCode() {
                // Given
                String email = "user@example.com";
                String code = "123456";
                when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                                .thenThrow(ExpiredCodeException.builder().message("Code expired").build());

                // When/Then
                assertThatThrownBy(() -> service.confirmSignup(email, code))
                                .isInstanceOf(AuthSignupException.class)
                                .extracting("code")
                                .isEqualTo("EXPIRED_CODE");
        }

        @Test
        @DisplayName("confirmSignup - user not found")
        void confirmSignup_userNotFound() {
                // Given
                String email = "nonexistent@example.com";
                String code = "123456";
                when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                                .thenThrow(UserNotFoundException.builder().message("User not found").build());

                // When/Then
                assertThatThrownBy(() -> service.confirmSignup(email, code))
                                .isInstanceOf(AuthSignupException.class)
                                .extracting("code")
                                .isEqualTo("USER_NOT_FOUND");
        }
}
