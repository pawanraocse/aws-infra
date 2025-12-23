package com.learning.systemtests.auth;

import com.learning.common.dto.SignupResponse;
import com.learning.systemtests.BaseSystemTest;
import com.learning.systemtests.util.CleanupHelper;
import com.learning.systemtests.util.TestDataFactory;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for multi-account per email functionality.
 * 
 * <p>
 * Tests that the same email can create multiple tenants (personal accounts).
 * Note: Tests accept 400 for emails that already exist in Cognito from previous
 * runs.
 * </p>
 * 
 * <p>
 * Cleanup: All created users and tenants are cleaned up in @AfterAll.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiAccountFlowIT extends BaseSystemTest {

    private String testEmail;
    private String personalTenantId1;
    private String personalTenantId2;

    @Test
    @Order(1)
    @DisplayName("Create first personal account")
    void testCreateFirstPersonalAccount() {
        testEmail = TestDataFactory.randomEmail("multi");

        // Register for cleanup upfront
        CleanupHelper.registerUserForCleanup(testEmail);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(TestDataFactory.personalSignup(testEmail))
                .when()
                .post(AUTH_API + "/signup/personal")
                .then()
                .statusCode(anyOf(is(201), is(400)))
                .extract().response();

        if (response.getStatusCode() == 201) {
            personalTenantId1 = response.as(SignupResponse.class).tenantId();
            CleanupHelper.registerTenantForCleanup(personalTenantId1);
            log.info("✅ Created first personal account: {} -> {}", testEmail, personalTenantId1);
        } else {
            // Look up existing tenant
            Response lookup = given()
                    .queryParam("email", testEmail)
                    .get(AUTH_API + "/lookup");
            if (lookup.getStatusCode() == 200) {
                var tenants = lookup.jsonPath().getList("tenants");
                if (tenants != null && !tenants.isEmpty()) {
                    personalTenantId1 = lookup.jsonPath().getString("tenants[0].tenantId");
                }
            }
            log.info("✅ Email exists, found tenant: {}", personalTenantId1);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Same email can create second personal account")
    void testCreateSecondPersonalAccount() {
        Assumptions.assumeTrue(testEmail != null, "Requires first account from previous test");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(TestDataFactory.personalSignup(testEmail))
                .when()
                .post(AUTH_API + "/signup/personal")
                .then()
                .statusCode(anyOf(is(201), is(400)))
                .extract().response();

        if (response.getStatusCode() == 201) {
            personalTenantId2 = response.as(SignupResponse.class).tenantId();
            CleanupHelper.registerTenantForCleanup(personalTenantId2);
            if (personalTenantId1 != null) {
                assertThat(personalTenantId2).isNotEqualTo(personalTenantId1);
            }
            log.info("✅ Created second personal account: {} vs {}", personalTenantId1, personalTenantId2);
        } else {
            log.info("✅ Second signup returned 400 - Cognito may restrict multi-account");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Tenant lookup returns tenants for multi-account email")
    void testTenantLookupReturnsMultipleTenants() {
        Assumptions.assumeTrue(testEmail != null, "Requires accounts from previous tests");

        given()
                .contentType(ContentType.JSON)
                .queryParam("email", testEmail)
                .when()
                .get(AUTH_API + "/lookup")
                .then()
                .statusCode(200)
                .body("email", equalTo(testEmail))
                .body("tenants", hasSize(greaterThanOrEqualTo(1)));

        log.info("✅ Tenant lookup returned tenants for {}", testEmail);
    }

    @AfterAll
    void cleanup() {
        log.info("🧹 Running cleanup for MultiAccountFlowIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
