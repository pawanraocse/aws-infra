package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import com.learning.systemtests.util.AuthHelper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class InvitationFlowIT extends BaseSystemTest {

    @Test
    void shouldInviteUserAndVerifyResponse() {
        // 1. Signup and Login
        AuthHelper.UserCredentials creds = AuthHelper.signup();
        String adminToken = AuthHelper.login(creds.email(), creds.password());

        // 2. Send Invitation
        String email = "newuser_" + UUID.randomUUID() + "@example.com";
        String roleId = "tenant-user";

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "roleId", roleId))
                .when()
                .post("/auth/api/v1/invitations")
                .then()
                .statusCode(201)
                .body("email", equalTo(email))
                .body("roleId", equalTo(roleId))
                .body("status", equalTo("PENDING"))
                .body("token", notNullValue());

        // Note: We are verifying the API response here.
        // Verification in the DB would require connecting to the tenant DB,
        // which is harder in a black-box system test unless we expose a verification
        // endpoint
        // or have direct DB access configured in the test runner.
        // For now, API verification gives us high confidence the flow is working.
    }
}
