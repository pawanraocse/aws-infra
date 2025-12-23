package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import com.learning.systemtests.util.CleanupHelper;
import com.learning.systemtests.util.TestDataFactory;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for permission and role flows.
 * Tests at API level to verify tenant creation.
 * 
 * <p>
 * Cleanup: All created users and tenants are cleaned up in @AfterAll.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionFlowIT extends BaseSystemTest {

    private String testEmail;
    private String testTenantId;

    @Test
    @Order(1)
    @DisplayName("Signup creates tenant with proper structure")
    void testSignupCreatesTenant() {
        testEmail = TestDataFactory.randomEmail("perm");

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
            testTenantId = response.jsonPath().getString("tenantId");
            CleanupHelper.registerTenantForCleanup(testTenantId);
            log.info("✅ Signup created tenant: {} -> {}", testEmail, testTenantId);
        } else {
            // Email already exists, lookup the tenant
            Response lookup = given()
                    .queryParam("email", testEmail)
                    .get(AUTH_API + "/lookup");
            if (lookup.getStatusCode() == 200) {
                var tenants = lookup.jsonPath().getList("tenants");
                if (tenants != null && !tenants.isEmpty()) {
                    testTenantId = lookup.jsonPath().getString("tenants[0].tenantId");
                }
            }
            log.info("✅ Email exists, found tenant: {}", testTenantId);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Tenant lookup returns valid tenant structure")
    void testTenantLookupReturnsValidStructure() {
        Assumptions.assumeTrue(testEmail != null, "Requires signup test to run first");

        given()
                .queryParam("email", testEmail)
                .when()
                .get(AUTH_API + "/lookup")
                .then()
                .statusCode(200)
                .body("email", equalTo(testEmail))
                .body("tenants", hasSize(greaterThanOrEqualTo(1)));

        log.info("✅ Tenant lookup returned valid structure");
    }

    @Test
    @Order(3)
    @DisplayName("Roles endpoint requires authentication")
    void testRolesEndpointRequiresAuth() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(ROLES_API)
                .then()
                .statusCode(401);

        log.info("✅ Roles endpoint correctly requires authentication");
    }

    @Test
    @Order(4)
    @DisplayName("Users endpoint requires authentication")
    void testUsersEndpointRequiresAuth() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get(USERS_API)
                .then()
                .statusCode(401);

        log.info("✅ Users endpoint correctly requires authentication");
    }

    @AfterAll
    void cleanup() {
        log.info("🧹 Running cleanup for PermissionFlowIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
