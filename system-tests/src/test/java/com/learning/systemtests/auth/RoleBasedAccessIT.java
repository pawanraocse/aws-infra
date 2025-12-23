package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import org.junit.jupiter.api.*;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Role-Based Access Control (RBAC).
 * 
 * <p>
 * <b>REQUIRES:</b> Verified Cognito user. Currently disabled.
 * </p>
 */
@Disabled("Requires verified Cognito user")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoleBasedAccessIT extends BaseSystemTest {

    private String adminToken;
    private String adminTenantId;

    @BeforeAll
    void setup() {
        log.info("RoleBasedAccessIT requires verified Cognito user - skipped");
    }

    @Test
    @Order(1)
    @DisplayName("Admin can list roles")
    void testAdminCanListRoles() {
        Assumptions.assumeTrue(adminToken != null, "Requires authenticated admin");
        log.info("Test requires verified user - skipped");
    }

    @Test
    @Order(2)
    @DisplayName("Unauthenticated user cannot access protected endpoints")
    void testUnauthenticatedAccessDenied() {
        // This test doesn't need login
        given()
                .when()
                .get(ROLES_API)
                .then()
                .statusCode(401);

        given()
                .when()
                .get(INVITATION_API)
                .then()
                .statusCode(401);

        given()
                .when()
                .get(ENTRIES_API)
                .then()
                .statusCode(401);

        log.info("✅ Unauthenticated access correctly denied");
    }

    @Test
    @Order(3)
    @DisplayName("Invalid token is rejected")
    void testInvalidTokenRejected() {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.token";

        given()
                .header("Authorization", "Bearer " + invalidToken)
                .when()
                .get(ENTRIES_API)
                .then()
                .statusCode(401);

        log.info("✅ Invalid token correctly rejected");
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
