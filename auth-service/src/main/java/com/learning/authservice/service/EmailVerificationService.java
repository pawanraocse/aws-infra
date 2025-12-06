package com.learning.authservice.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.exception.AuthSignupException;

/**
 * Service for handling email verification operations.
 * Interacts with AWS Cognito for verification code management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    /**
     * Resend verification code to user's email.
     * 
     * @param email User's email address
     * @throws AuthSignupException if resend fails
     */
    public void resendVerificationCode(String email) {
        try {
            ResendConfirmationCodeRequest request = ResendConfirmationCodeRequest.builder()
                    .clientId(cognitoProperties.getClientId())
                    .username(email)
                    .build();

            ResendConfirmationCodeResponse response = cognitoClient.resendConfirmationCode(request);

            log.info("Verification code resent to: {} via {}", email,
                    response.codeDeliveryDetails().deliveryMedium());

        } catch (UserNotFoundException e) {
            log.warn("User not found for resend verification: {}", email);
            throw new AuthSignupException("USER_NOT_FOUND", "User not found", e);

        } catch (InvalidParameterException e) {
            log.warn("User already confirmed: {}", email);
            throw new AuthSignupException("ALREADY_CONFIRMED", "User already verified", e);

        } catch (LimitExceededException e) {
            log.warn("Rate limit exceeded for: {}", email);
            throw new AuthSignupException("RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please try again later.", e);

        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to resend verification code: {}", e.getMessage());
            throw new AuthSignupException("RESEND_FAILED",
                    "Failed to resend verification email", e);
        }
    }

    /**
     * Confirm user signup with verification code.
     * 
     * @param email User's email address
     * @param code  Verification code from email
     * @throws AuthSignupException if confirmation fails
     */
    public void confirmSignup(String email, String code) {
        try {
            ConfirmSignUpRequest request = ConfirmSignUpRequest.builder()
                    .clientId(cognitoProperties.getClientId())
                    .username(email)
                    .confirmationCode(code)
                    .build();

            cognitoClient.confirmSignUp(request);

            log.info("User confirmed successfully: {}", email);

        } catch (CodeMismatchException e) {
            log.warn("Invalid verification code for: {}", email);
            throw new AuthSignupException("INVALID_CODE", "Invalid verification code", e);

        } catch (ExpiredCodeException e) {
            log.warn("Expired verification code for: {}", email);
            throw new AuthSignupException("EXPIRED_CODE",
                    "Verification code expired. Please request a new one.", e);

        } catch (UserNotFoundException e) {
            log.warn("User not found for confirmation: {}", email);
            throw new AuthSignupException("USER_NOT_FOUND", "User not found", e);

        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to confirm signup: {}", e.getMessage());
            throw new AuthSignupException("CONFIRM_FAILED",
                    "Failed to confirm signup", e);
        }
    }
}
