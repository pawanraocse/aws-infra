package com.learning.authservice.signup;

import com.learning.authservice.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Handles user registration in Cognito.
 * Supports multi-account per email by checking if user exists before
 * registration.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CognitoUserRegistrar {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    /**
     * Result of registration attempt.
     */
    public enum RegistrationResult {
        /** New user created, may need email verification */
        CREATED,
        /** User already exists in Cognito, skipped creation */
        ALREADY_EXISTS
    }

    /**
     * Check if a user exists in Cognito.
     * 
     * @param email user's email address
     * @return true if user exists, false otherwise
     */
    public boolean userExists(String email) {
        log.debug("Checking if user exists in Cognito: email={}", email);

        try {
            AdminGetUserRequest request = AdminGetUserRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(email)
                    .build();

            cognitoClient.adminGetUser(request);
            log.debug("User exists in Cognito: email={}", email);
            return true;

        } catch (UserNotFoundException e) {
            log.debug("User does not exist in Cognito: email={}", email);
            return false;
        } catch (CognitoIdentityProviderException e) {
            log.error("Error checking user existence: email={} error={}",
                    email, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Failed to check user existence: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Register a user in Cognito if they don't exist.
     * If user already exists, returns ALREADY_EXISTS without error.
     * 
     * This enables multi-account per email feature where the same user
     * can own multiple organizations.
     * 
     * @param email    user's email
     * @param password user's password (ignored if user exists)
     * @param name     user's display name
     * @param tenantId tenant ID for the new workspace
     * @param role     initial role (e.g., "admin")
     * @return RegistrationResult indicating if user was created or already existed
     */
    public RegistrationResult registerIfNotExists(String email, String password, String name,
            String tenantId, String role) {
        log.info("RegisterIfNotExists: email={} tenantId={}", email, tenantId);

        // Check if user already exists
        if (userExists(email)) {
            log.info("User already exists, skipping Cognito registration: email={}", email);
            return RegistrationResult.ALREADY_EXISTS;
        }

        // User doesn't exist, register them
        register(email, password, name, tenantId, role);
        return RegistrationResult.CREATED;
    }

    /**
     * Register a user in Cognito via signUp API.
     * This enforces email verification for both personal and organization signups.
     * 
     * NOTE: tenantType is NOT stored in Cognito - frontend looks it up from
     * platform DB.
     * 
     * @param email    user's email
     * @param password user's password
     * @param name     user's display name
     * @param tenantId tenant ID to associate with user
     * @param role     initial role (e.g., "admin")
     * @return true if user was auto-confirmed, false if email verification is
     *         pending
     */
    public boolean register(String email, String password, String name, String tenantId, String role) {
        log.info("Registering user in Cognito: email={} tenantId={} role={}", email, tenantId, role);

        try {
            String secretHash = calculateSecretHash(email);

            SignUpRequest signUpRequest = SignUpRequest.builder()
                    .clientId(cognitoProperties.getClientId())
                    .username(email)
                    .password(password)
                    .secretHash(secretHash)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("name").value(name).build())
                    .clientMetadata(Map.of(
                            "tenantId", tenantId,
                            "role", role))
                    .build();

            SignUpResponse response = cognitoClient.signUp(signUpRequest);

            log.info("Cognito user registered: email={} confirmed={}", email, response.userConfirmed());
            return response.userConfirmed();

        } catch (UsernameExistsException e) {
            log.error("User already exists: email={}", email);
            throw new IllegalArgumentException("User with email " + email + " already exists");
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito error registering user: email={} error={}", email, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Failed to register user: " + e.awsErrorDetails().errorMessage());
        }
    }

    private String calculateSecretHash(String username) {
        try {
            String message = username + cognitoProperties.getClientId();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    cognitoProperties.getClientSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating secret hash", e);
        }
    }
}
