package com.learning.systemtests.util;

import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.SignupResponse;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import static com.learning.systemtests.config.TestConfig.*;

/**
 * Utility class to handle authentication for E2E tests.
 * <p>
 * IMPORTANT: All requests go through the Gateway (port 8080), not directly to
 * auth-service.
 * This tests the real production routing.
 * </p>
 */
public class AuthHelper {

    private static final Logger log = LoggerFactory.getLogger(AuthHelper.class);

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;

    private static CognitoIdentityProviderClient cognitoClient;

    private AuthHelper() {
        // Utility class
    }

    // ============================================================
    // Login Methods
    // ============================================================

    /**
     * Authenticates with the given credentials and returns a JWT access token.
     * Routes through Gateway to test real production flow.
     *
     * @param email    User email
     * @param password User password
     * @return JWT access token
     * @throws RuntimeException if login fails after retries
     */
    public static String login(String email, String password) {
        log.info("Attempting login for user: {}", email);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);

        Response response = executeWithRetry(() -> RestAssured.given()
                .baseUri(GATEWAY_URL)
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post(AUTH_API + "/login")
                .then()
                .extract()
                .response());

        if (response.getStatusCode() != 200) {
            String errorMessage = String.format(
                    "Login failed with status %d: %s",
                    response.getStatusCode(),
                    response.getBody().asString());
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }

        String accessToken = response.jsonPath().getString("accessToken");
        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("Access token not found in login response");
        }

        log.info("✅ Login successful for user: {}", email);
        return accessToken;
    }

    /**
     * Login with default test credentials from environment.
     */
    public static String getAccessToken() {
        String email = System.getenv().getOrDefault("TEST_USER_EMAIL", "test@example.com");
        String password = System.getenv().getOrDefault("TEST_USER_PASSWORD", DEFAULT_TEST_PASSWORD);
        return login(email, password);
    }

    // ============================================================
    // Cognito Admin Operations
    // ============================================================

    /**
     * Confirm a user in Cognito using AdminConfirmSignUp and set their password.
     * This bypasses email verification and ensures the user can login.
     *
     * @param email    User email to confirm
     * @param password Password to set for the user
     * @return true if confirmed successfully, false otherwise
     */
    public static boolean confirmUser(String email, String password) {
        if (COGNITO_USER_POOL_ID == null || COGNITO_USER_POOL_ID.isEmpty()) {
            log.warn("Cognito User Pool ID not configured, cannot confirm user: {}", email);
            return false;
        }

        try {
            CognitoIdentityProviderClient client = getCognitoClient();

            // Step 1: Confirm the user (bypass email verification)
            client.adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
                    .userPoolId(COGNITO_USER_POOL_ID)
                    .username(email)
                    .build());
            log.info("✅ Confirmed Cognito user: {}", email);

            // Step 2: Set the password as permanent (required for login after admin
            // confirm)
            client.adminSetUserPassword(
                    software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest.builder()
                            .userPoolId(COGNITO_USER_POOL_ID)
                            .username(email)
                            .password(password)
                            .permanent(true)
                            .build());
            log.info("✅ Set permanent password for user: {}", email);

            // Wait for Cognito to propagate changes (eventual consistency)
            Thread.sleep(2000);

            return true;
        } catch (UserNotFoundException e) {
            log.warn("User not found in Cognito: {}", email);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true; // Still succeeded, just interrupted
        } catch (Exception e) {
            log.warn("Failed to confirm user {}: {}", email, e.getMessage());
            return false;
        }
    }

    /**
     * Confirm a user with the default test password.
     */
    public static boolean confirmUser(String email) {
        return confirmUser(email, DEFAULT_TEST_PASSWORD);
    }

    /**
     * Signup a new user AND confirm them in Cognito.
     * This is the preferred method for tests that need to login.
     *
     * @return UserCredentials for the confirmed user
     */
    public static UserCredentials signupAndConfirm() {
        UserCredentials creds = signup();
        confirmUser(creds.email(), creds.password());
        // Register for cleanup
        CleanupHelper.registerUserForCleanup(creds.email());
        CleanupHelper.registerTenantForCleanup(creds.tenantId());
        return creds;
    }

    /**
     * Signup with specified email AND confirm in Cognito.
     */
    public static UserCredentials signupAndConfirmWithEmail(String email) {
        UserCredentials creds = signupWithEmail(email);
        confirmUser(email, creds.password());
        // Register for cleanup
        CleanupHelper.registerUserForCleanup(email);
        CleanupHelper.registerTenantForCleanup(creds.tenantId());
        return creds;
    }

    private static CognitoIdentityProviderClient getCognitoClient() {
        if (cognitoClient == null) {
            cognitoClient = CognitoIdentityProviderClient.builder()
                    .region(Region.of(AWS_REGION))
                    .build();
        }
        return cognitoClient;
    }

    // ============================================================
    // Signup Methods
    // ============================================================

    /**
     * Signs up a new user with random credentials and returns the credentials.
     * Routes through Gateway to test real production flow.
     *
     * @return UserCredentials containing email and password
     */
    public static UserCredentials signup() {
        String email = TestDataFactory.randomEmail();
        String password = DEFAULT_TEST_PASSWORD;
        String name = TestDataFactory.randomName();

        PersonalSignupRequest request = new PersonalSignupRequest(email, password, name);

        log.info("Attempting signup for user: {}", email);

        Response response = executeWithRetry(() -> RestAssured.given()
                .baseUri(GATEWAY_URL)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post(AUTH_API + "/signup/personal")
                .then()
                .extract()
                .response());

        if (response.getStatusCode() != 201) {
            String errorMessage = String.format(
                    "Signup failed with status %d: %s",
                    response.getStatusCode(),
                    response.getBody().asString());
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }

        SignupResponse signupResponse = response.as(SignupResponse.class);
        log.info("✅ Signup successful. TenantId: {}", signupResponse.tenantId());

        return new UserCredentials(email, password, signupResponse.tenantId());
    }

    /**
     * Signs up with specified email and returns credentials.
     */
    public static UserCredentials signupWithEmail(String email) {
        String password = DEFAULT_TEST_PASSWORD;
        String name = TestDataFactory.randomName();

        PersonalSignupRequest request = new PersonalSignupRequest(email, password, name);

        log.info("Attempting signup for user: {}", email);

        Response response = executeWithRetry(() -> RestAssured.given()
                .baseUri(GATEWAY_URL)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post(AUTH_API + "/signup/personal")
                .then()
                .extract()
                .response());

        if (response.getStatusCode() != 201) {
            String errorMessage = String.format(
                    "Signup failed with status %d: %s",
                    response.getStatusCode(),
                    response.getBody().asString());
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }

        SignupResponse signupResponse = response.as(SignupResponse.class);
        log.info("✅ Signup successful. TenantId: {}", signupResponse.tenantId());

        return new UserCredentials(email, password, signupResponse.tenantId());
    }

    // ============================================================
    // Full Auth Response
    // ============================================================

    /**
     * Returns a complete authentication response including all tokens.
     *
     * @param email    User email
     * @param password User password
     * @return AuthResponse object
     */
    public static AuthResponse getFullAuthResponse(String email, String password) {
        log.info("Getting full auth response for user: {}", email);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);

        Response response = RestAssured.given()
                .baseUri(GATEWAY_URL)
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post(AUTH_API + "/login")
                .then()
                .statusCode(200)
                .extract()
                .response();

        return new AuthResponse(
                response.jsonPath().getString("accessToken"),
                response.jsonPath().getString("idToken"),
                response.jsonPath().getString("refreshToken"),
                response.jsonPath().getString("tokenType"),
                response.jsonPath().getLong("expiresIn"),
                response.jsonPath().getString("userId"),
                response.jsonPath().getString("email"));
    }

    // ============================================================
    // Account Operations
    // ============================================================

    /**
     * Delete account for the authenticated user.
     *
     * @param token        JWT access token
     * @param confirmation Must be "DELETE" to confirm
     * @return Response object
     */
    public static Response deleteAccount(String token, String confirmation) {
        log.info("Attempting to delete account with confirmation: {}", confirmation);

        String payload = String.format("""
                {
                    "confirmation": "%s"
                }
                """, confirmation);

        return RestAssured.given()
                .baseUri(GATEWAY_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(ACCOUNT_API + "/delete")
                .then()
                .extract()
                .response();
    }

    // ============================================================
    // Password Reset
    // ============================================================

    /**
     * Request password reset for an email.
     *
     * @param email User email
     * @return Response object
     */
    public static Response forgotPassword(String email) {
        log.info("Requesting password reset for: {}", email);

        String payload = String.format("""
                {
                    "email": "%s"
                }
                """, email);

        return RestAssured.given()
                .baseUri(GATEWAY_URL)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(AUTH_API + "/forgot-password")
                .then()
                .extract()
                .response();
    }

    // ============================================================
    // Retry Helper
    // ============================================================

    /**
     * Execute a request with retry logic for transient failures.
     */
    private static Response executeWithRetry(java.util.function.Supplier<Response> request) {
        Response response = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = request.get();
                // If we get a response (even error), return it
                if (response != null) {
                    return response;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Request attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Request failed after " + MAX_RETRIES + " attempts", lastException);
    }

    // ============================================================
    // DTOs
    // ============================================================

    /**
     * User credentials with tenant ID.
     */
    public record UserCredentials(String email, String password, String tenantId) {
        /**
         * Constructor without tenantId for backward compatibility.
         */
        public UserCredentials(String email, String password) {
            this(email, password, null);
        }
    }

    /**
     * Complete authentication response.
     */
    public record AuthResponse(
            String accessToken,
            String idToken,
            String refreshToken,
            String tokenType,
            Long expiresIn,
            String userId,
            String email) {
    }
}
