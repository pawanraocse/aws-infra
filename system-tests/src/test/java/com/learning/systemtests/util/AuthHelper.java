package com.learning.systemtests.util;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to handle authentication for E2E tests.
 * <p>
 * This class provides methods to programmatically login and retrieve JWT tokens
 * from the Auth Service running on localhost.
 * </p>
 */
public class AuthHelper {

    private static final Logger log = LoggerFactory.getLogger(AuthHelper.class);
    
    private static final String AUTH_SERVICE_URL = "http://localhost:8081/auth";
    
    // Test user credentials (should match a user in your Cognito user pool)
    // TODO: Replace with actual test user credentials or load from environment variables
    private static final String TEST_USER_EMAIL = System.getenv().getOrDefault("TEST_USER_EMAIL", "test@example.com");
    private static final String TEST_USER_PASSWORD = System.getenv().getOrDefault("TEST_USER_PASSWORD", "Test123!");

    /**
     * Authenticates the test user and returns a valid JWT access token.
     *
     * @return JWT access token
     * @throws RuntimeException if login fails
     */
    public static String getAccessToken() {
        return login(TEST_USER_EMAIL, TEST_USER_PASSWORD);
    }

    /**
     * Authenticates with the given credentials and returns a JWT access token.
     *
     * @param email    User email
     * @param password User password
     * @return JWT access token
     * @throws RuntimeException if login fails
     */
    public static String login(String email, String password) {
        log.info("Attempting login for user: {}", email);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);

        Response response = RestAssured.given()
                .baseUri(AUTH_SERVICE_URL)
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/login")
                .then()
                .extract()
                .response();

        if (response.getStatusCode() != 200) {
            String errorMessage = String.format(
                    "Login failed with status %d: %s",
                    response.getStatusCode(),
                    response.getBody().asString()
            );
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }

        String accessToken = response.jsonPath().getString("accessToken");
        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("Access token not found in login response");
        }

        log.info("Login successful for user: {}", email);
        return accessToken;
    }

    /**
     * Returns a complete authentication response including access token, ID token, etc.
     *
     * @return AuthResponse object
     */
    public static AuthResponse getFullAuthResponse() {
        return getFullAuthResponse(TEST_USER_EMAIL, TEST_USER_PASSWORD);
    }

    /**
     * Returns a complete authentication response for the given credentials.
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
                .baseUri(AUTH_SERVICE_URL)
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/login")
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
                response.jsonPath().getString("email")
        );
    }

    /**
     * Simple DTO to hold authentication response data.
     */
    public record AuthResponse(
            String accessToken,
            String idToken,
            String refreshToken,
            String tokenType,
            Long expiresIn,
            String userId,
            String email
    ) {
    }
}
