package com.learning.authservice.service;

import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.exception.AuthSignupException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
            String secretHash = calculateSecretHash(email);// Required for app clients with secrets

            ResendConfirmationCodeRequest.Builder requestBuilder = ResendConfirmationCodeRequest.builder()
                    .clientId(cognitoProperties.getClientId())
                    .username(email);

            if (secretHash != null) {
                requestBuilder.secretHash(secretHash);
            }

            ResendConfirmationCodeResponse response = cognitoClient.resendConfirmationCode(requestBuilder.build());

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
            String secretHash = calculateSecretHash(email);

            ConfirmSignUpRequest.Builder requestBuilder = ConfirmSignUpRequest.builder()
                    .clientId(cognitoProperties.getClientId())
                    .username(email)
                    .confirmationCode(code);

            if (secretHash != null) {
                requestBuilder.secretHash(secretHash);
            }

            cognitoClient.confirmSignUp(requestBuilder.build());

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

    /**
     * Calculate SECRET_HASH for Cognito API calls.
     * Required when app client has a secret configured.
     */
    private String calculateSecretHash(String username) {
        String clientSecret = cognitoProperties.getClientSecret();
        if (clientSecret == null || clientSecret.isBlank()) {
            return null;
        }

        try {
            String message = username + cognitoProperties.getClientId();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            log.error("Failed to calculate secret hash: {}", e.getMessage());
            throw new RuntimeException("Failed to calculate secret hash", e);
        }
    }
}
