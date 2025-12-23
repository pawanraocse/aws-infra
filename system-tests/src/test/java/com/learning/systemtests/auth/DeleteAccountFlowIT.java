package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import com.learning.systemtests.util.AuthHelper;
import com.learning.systemtests.util.CleanupHelper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the Delete Account flow.
 * 
 * <p>
 * Uses AdminConfirmSignUp to auto-verify test users.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeleteAccountFlowIT extends BaseSystemTest {

    private String userToken;
    private String userEmail;

    @BeforeAll
    void setup() {
        log.info("Setting up user for DeleteAccountFlowIT...");
        try {
            AuthHelper.UserCredentials creds = AuthHelper.signupAndConfirm();
            userEmail = creds.email();
            userToken = AuthHelper.login(creds.email(), creds.password());
            log.info("✅ Setup complete: {}", userEmail);
        } catch (Exception e) {
            log.warn("Setup failed: {} - tests will be skipped", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Delete account without confirmation - returns 400")
    void testDeleteAccountWithoutConfirmation() {
        Assumptions.assumeTrue(userToken != null, "Requires authenticated user");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post(ACCOUNT_API + "/delete")
                .then()
                .statusCode(400);

        log.info("✅ Missing confirmation correctly rejected");
    }

    @Test
    @Order(2)
    @DisplayName("Delete account with wrong confirmation - returns 400")
    void testDeleteAccountWrongConfirmation() {
        Assumptions.assumeTrue(userToken != null, "Requires authenticated user");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("{\"confirmation\": \"WRONG\"}")
                .when()
                .post(ACCOUNT_API + "/delete")
                .then()
                .statusCode(400);

        log.info("✅ Wrong confirmation correctly rejected");
    }

    @Test
    @Order(3)
    @DisplayName("Delete account with proper confirmation - success")
    void testDeleteAccountSuccess() {
        Assumptions.assumeTrue(userToken != null, "Requires authenticated user");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("{\"confirmation\": \"DELETE\"}")
                .when()
                .post(ACCOUNT_API + "/delete")
                .then()
                .statusCode(anyOf(is(200), is(204)));

        log.info("✅ Account deleted successfully");

        // Token should now be invalid
        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .get(ENTRIES_API)
                .then()
                .statusCode(anyOf(is(401), is(403)));

        log.info("✅ Token invalidated after delete");
    }

    @AfterAll
    void cleanup() {
        // Note: Account already deleted in test, but cleanup any leftover data
        log.info("🧹 Running cleanup for DeleteAccountFlowIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
