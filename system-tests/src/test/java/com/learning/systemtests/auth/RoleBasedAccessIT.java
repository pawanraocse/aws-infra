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
 * Integration tests for Role-Based Access Control (RBAC).
 * 
 * <p>
 * Uses AdminConfirmSignUp to auto-verify test users.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoleBasedAccessIT extends BaseSystemTest {

    private String adminToken;
    private String adminTenantId;

    @BeforeAll
    void setup() {
        log.info("Setting up admin user for RoleBasedAccessIT...");
        try {
            AuthHelper.UserCredentials creds = AuthHelper.signupAndConfirm();
            adminTenantId = creds.tenantId();
            adminToken = AuthHelper.login(creds.email(), creds.password());
            log.info("✅ Setup complete: tenant={}", adminTenantId);
        } catch (Exception e) {
            log.warn("Setup failed: {} - authenticated tests will be skipped", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Admin can list roles")
    void testAdminCanListRoles() {
        Assumptions.assumeTrue(adminToken != null, "Requires authenticated admin");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .when()
                .get(ROLES_API)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1))); // At least one role

        log.info("✅ Admin can list roles");
    }

    @Test
    @Order(2)
    @DisplayName("Admin can list users in tenant")
    void testAdminCanListUsers() {
        Assumptions.assumeTrue(adminToken != null, "Requires authenticated admin");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .when()
                .get(USERS_API)
                .then()
                .statusCode(200);

        log.info("✅ Admin can list users");
    }

    @Test
    @Order(3)
    @DisplayName("Unauthenticated user cannot access protected endpoints")
    void testUnauthenticatedAccessDenied() {
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
    @Order(4)
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

    @AfterAll
    void cleanup() {
        log.info("🧹 Running cleanup for RoleBasedAccessIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
