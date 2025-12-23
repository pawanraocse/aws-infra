package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
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
 * <b>REQUIRES:</b> Verified Cognito user. Currently disabled.
 * </p>
 */
@Disabled("Requires verified Cognito user")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvitationFlowIT extends BaseSystemTest {

    private String adminToken;
    private String tenantId;

    @BeforeAll
    void setup() {
        // Requires verified user
        log.info("InvitationFlowIT requires verified Cognito user - skipped");
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
                .statusCode(201)
                .body("email", equalTo(inviteeEmail))
                .body("roleId", equalTo(roleId))
                .body("status", equalTo("PENDING"))
                .body("token", notNullValue());

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
                .statusCode(200)
                .body("$", not(empty()));

        log.info("✅ Listed pending invitations");
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
