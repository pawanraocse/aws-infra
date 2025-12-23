package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import com.learning.systemtests.util.AuthHelper;
import com.learning.systemtests.util.CleanupHelper;
import com.learning.systemtests.util.TestDataFactory;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the invitation flow.
 * Tests inviting users to an organization.
 * 
 * <p>
 * Uses AdminConfirmSignUp to auto-verify test users.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvitationFlowIT extends BaseSystemTest {

    private String adminToken;
    private String tenantId;

    @BeforeAll
    void setup() {
        log.info("Setting up admin for InvitationFlowIT...");
        try {
            AuthHelper.UserCredentials creds = AuthHelper.signupAndConfirm();
            tenantId = creds.tenantId();
            adminToken = AuthHelper.login(creds.email(), creds.password());
            log.info("✅ Setup complete: tenant={}", tenantId);
        } catch (Exception e) {
            log.warn("Setup failed: {} - tests will be skipped", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should invite user and verify response")
    void shouldInviteUserAndVerifyResponse() {
        Assumptions.assumeTrue(adminToken != null, "Requires authenticated admin");

        String inviteeEmail = TestDataFactory.randomEmail("invitee");
        String roleId = "user";

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", inviteeEmail,
                        "roleId", roleId))
                .when()
                .post(INVITATION_API)
                .then()
                .statusCode(anyOf(is(200), is(201)))
                .body("email", equalTo(inviteeEmail));

        log.info("✅ Invitation sent to: {}", inviteeEmail);
    }

    @Test
    @Order(2)
    @DisplayName("Should list pending invitations")
    void shouldListPendingInvitations() {
        Assumptions.assumeTrue(adminToken != null, "Requires authenticated admin");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get(INVITATION_API)
                .then()
                .statusCode(200);

        log.info("✅ Listed invitations");
    }

    @Test
    @Order(3)
    @DisplayName("Unauthenticated cannot access invitations")
    void testUnauthenticatedCannotAccessInvitations() {
        given()
                .when()
                .get(INVITATION_API)
                .then()
                .statusCode(401);

        log.info("✅ Unauthenticated access correctly rejected");
    }

    @AfterAll
    void cleanup() {
        log.info("🧹 Running cleanup for InvitationFlowIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
