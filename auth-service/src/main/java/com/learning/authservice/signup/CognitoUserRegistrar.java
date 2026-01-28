package com.learning.authservice.signup;

import com.learning.authservice.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
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

    public record UserRegistration(
            String userSub,
            boolean confirmed,
            boolean alreadyExisted) {
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
            getCheckUserRequest(email);
            return true;
        } catch (UserNotFoundException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking user existence: {}", e.getMessage());
            throw new RuntimeException("Failed to check user existence", e);
        }
    }

    private AdminGetUserResponse getCheckUserRequest(String email) {
        return cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .username(email)
                .build());
    }

    /**
     * Register a user in Cognito if they don't exist.
     * If user already exists, returns existing user details.
     */
    public UserRegistration registerIfNotExists(String email, String password, String name,
            String tenantId, String role) {
        log.info("RegisterIfNotExists: email={} tenantId={}", email, tenantId);

        // Check if user already exists
        try {
            AdminGetUserResponse existingUser = getCheckUserRequest(email);
            log.info("User already exists, skipping Cognito registration: email={}", email);

            String sub = existingUser.userAttributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Existing user has no sub"));

            boolean confirmed = "CONFIRMED".equals(existingUser.userStatusAsString());

            return new UserRegistration(sub, confirmed, true);

        } catch (UserNotFoundException e) {
            // User doesn't exist, proceed with registration
        }

        // User doesn't exist, register them
        SignUpResponse response = register(email, password, name, tenantId, role);
        return new UserRegistration(response.userSub(), response.userConfirmed(), false);
    }

    /**
     * Register a user in Cognito via signUp API.
     */
    public SignUpResponse register(String email, String password, String name, String tenantId, String role) {
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

            log.info("Cognito user registered: email={} confirmed={} sub={}",
                    email, response.userConfirmed(), response.userSub());
            return response;

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
