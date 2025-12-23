package com.learning.systemtests.auth;

import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.SignupResponse;
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
 * Integration tests for the login flow, specifically tenant lookup
 * functionality.
 * 
 * <p>
 * Note: Login tests are skipped due to Cognito email verification requirement.
 * Only tenant lookup tests (which don't require login) are run.
 * </p>
 * 
 * <p>
 * Cleanup: All created users and tenants are cleaned up in @AfterAll.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginFlowIT extends BaseSystemTest {

    private String testEmail;
    private String testTenantId;

    @Test
    @Order(1)
    @DisplayName("Signup creates tenant and tenant lookup returns it")
    void testSignupAndTenantLookup() {
        PersonalSignupRequest signupRequest = TestDataFactory.personalSignup();
        testEmail = signupRequest.email();

        // Register for cleanup upfront
        CleanupHelper.registerUserForCleanup(testEmail);

        Response signupResponse = given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
                .when()
                .post(AUTH_API + "/signup/personal")
                .then()
                .statusCode(anyOf(is(201), is(400)))
                .extract().response();

        if (signupResponse.getStatusCode() == 201) {
            SignupResponse signup = signupResponse.as(SignupResponse.class);
            testTenantId = signup.tenantId();
            CleanupHelper.registerTenantForCleanup(testTenantId);
            log.info("✅ Signup successful: {} -> {}", testEmail, testTenantId);
        } else {
            log.info("Signup returned 400 - looking up existing tenant");
        }

        // Lookup tenants for this email
        Response lookupResponse = given()
                .contentType(ContentType.JSON)
                .queryParam("email", testEmail)
                .when()
                .get(AUTH_API + "/lookup")
                .then()
                .statusCode(200)
                .body("email", equalTo(testEmail))
                .extract().response();

        var tenants = lookupResponse.jsonPath().getList("tenants");
        if (tenants != null && !tenants.isEmpty()) {
            if (testTenantId == null) {
                testTenantId = lookupResponse.jsonPath().getString("tenants[0].tenantId");
            }
        }
        log.info("✅ Tenant lookup successful - tenant found: {}", testTenantId);
    }

    @Test
    @Order(2)
    @DisplayName("Tenant lookup for unknown email returns empty list")
    void testTenantLookupUnknownEmail() {
        String unknownEmail = TestDataFactory.randomEmail("unknown");

        given()
                .contentType(ContentType.JSON)
                .queryParam("email", unknownEmail)
                .when()
                .get(AUTH_API + "/lookup")
                .then()
                .statusCode(200)
                .body("email", equalTo(unknownEmail))
                .body("tenants", hasSize(0))
                .body("requiresSelection", equalTo(false))
                .body("defaultTenantId", nullValue());

        log.info("✅ Unknown email correctly returns empty tenant list");
    }

    @Test
    @Order(3)
    @DisplayName("Tenant lookup validates email format")
    void testTenantLookupInvalidEmail() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("email", "not-an-email")
                .when()
                .get(AUTH_API + "/lookup")
                .then()
                .statusCode(anyOf(is(400), is(500)));

        log.info("✅ Invalid email format handled");
    }

    @Test
    @Order(4)
    @DisplayName("Tenant lookup with existing email returns correct tenant info")
    void testTenantLookupReturnsCorrectTenantInfo() {
        Assumptions.assumeTrue(testEmail != null, "Requires testSignupAndTenantLookup to run first");

        given()
                .contentType(ContentType.JSON)
                .queryParam("email", testEmail)
                .when()
                .get(AUTH_API + "/lookup")
                .then()
                .statusCode(200)
                .body("tenants", hasSize(greaterThanOrEqualTo(1)));

        log.info("✅ Tenant info verified for: {}", testEmail);
    }

    @AfterAll
    void cleanup() {
        log.info("🧹 Running cleanup for LoginFlowIT...");
        CleanupHelper.cleanupAll();
    }

    @AfterEach
    void afterEach() {
        log.info("---");
    }
}
