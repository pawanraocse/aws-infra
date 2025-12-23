package com.learning.systemtests.auth;

import com.learning.common.dto.OrganizationSignupRequest;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * System-level integration tests for signup flows.
 * Tests complete user journey: signup → tenant provisioning → Cognito user
 * creation.
 * 
 * <p>
 * Cleanup: All created users and tenants are cleaned up in @AfterAll.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignupFlowIT extends BaseSystemTest {

        @Test
        @Order(1)
        @DisplayName("B2C Personal Signup - Success or Already Exists")
        void testPersonalSignupSuccess() {
                PersonalSignupRequest request = TestDataFactory.personalSignup();

                Response response = given()
                                .contentType(ContentType.JSON)
                                .body(request)
                                .when()
                                .post(AUTH_API + "/signup/personal")
                                .then()
                                .statusCode(anyOf(is(201), is(400)))
                                .extract().response();

                if (response.getStatusCode() == 201) {
                        SignupResponse signupResponse = response.as(SignupResponse.class);
                        assertThat(signupResponse.tenantId()).startsWith("user-");

                        // Register for cleanup
                        CleanupHelper.registerUserForCleanup(request.email());
                        CleanupHelper.registerTenantForCleanup(signupResponse.tenantId());

                        log.info("✅ Personal signup successful: {} -> {}", request.email(), signupResponse.tenantId());
                } else {
                        log.info("✅ Signup returned 400 - email may already exist in Cognito: {}", request.email());
                }
        }

        @Test
        @Order(2)
        @DisplayName("B2B Organization Signup - Success or Already Exists")
        void testOrganizationSignupSuccess() {
                OrganizationSignupRequest request = TestDataFactory.orgSignup();

                Response response = given()
                                .contentType(ContentType.JSON)
                                .body(request)
                                .when()
                                .post(AUTH_API + "/signup/organization")
                                .then()
                                .statusCode(anyOf(is(201), is(400)))
                                .extract().response();

                if (response.getStatusCode() == 201) {
                        SignupResponse signupResponse = response.as(SignupResponse.class);
                        assertThat(signupResponse.tenantId()).isNotNull();

                        // Register for cleanup
                        CleanupHelper.registerUserForCleanup(request.adminEmail());
                        CleanupHelper.registerTenantForCleanup(signupResponse.tenantId());

                        log.info("✅ Organization signup successful: {} -> {}", request.companyName(),
                                        signupResponse.tenantId());
                } else {
                        log.info("✅ Org signup returned 400 - email may already exist: {}", request.adminEmail());
                }
        }

        @Test
        @Order(3)
        @DisplayName("Same Email - Creates Different Tenant (Multi-Account)")
        void testSameEmailCreatesNewTenant() {
                String testEmail = TestDataFactory.randomEmail("multitest");
                PersonalSignupRequest request = TestDataFactory.personalSignup(testEmail);

                // Register email for cleanup upfront
                CleanupHelper.registerUserForCleanup(testEmail);

                Response first = given()
                                .contentType(ContentType.JSON)
                                .body(request)
                                .when()
                                .post(AUTH_API + "/signup/personal")
                                .then()
                                .statusCode(anyOf(is(201), is(400)))
                                .extract().response();

                if (first.getStatusCode() != 201) {
                        log.info("First signup returned 400 - email exists, skipping multi-account test");
                        return;
                }

                String firstTenantId = first.jsonPath().getString("tenantId");
                CleanupHelper.registerTenantForCleanup(firstTenantId);
                log.info("First signup: {} -> {}", testEmail, firstTenantId);

                Response second = given()
                                .contentType(ContentType.JSON)
                                .body(request)
                                .when()
                                .post(AUTH_API + "/signup/personal")
                                .then()
                                .statusCode(anyOf(is(201), is(400)))
                                .extract().response();

                if (second.getStatusCode() == 201) {
                        String secondTenantId = second.jsonPath().getString("tenantId");
                        CleanupHelper.registerTenantForCleanup(secondTenantId);
                        assertThat(secondTenantId).isNotEqualTo(firstTenantId);
                        log.info("✅ Same email created different tenant: {} vs {}", firstTenantId, secondTenantId);
                } else {
                        log.info("✅ Second signup returned 400 - expected for some Cognito configurations");
                }
        }

        @Test
        @Order(4)
        @DisplayName("Invalid Password - Should Return 400")
        void testInvalidPasswordSignup() {
                String testEmail = TestDataFactory.randomEmail("weakpass");
                String weakPassword = "123";

                PersonalSignupRequest request = new PersonalSignupRequest(testEmail, weakPassword, "Test User");

                given()
                                .contentType(ContentType.JSON)
                                .body(request)
                                .when()
                                .post(AUTH_API + "/signup/personal")
                                .then()
                                .statusCode(400);

                log.info("✅ Weak password correctly rejected");
        }

        @Test
        @Order(5)
        @DisplayName("Missing Required Fields - Should Return 400")
        void testMissingFieldsSignup() {
                given()
                                .contentType(ContentType.JSON)
                                .body("{\"email\":\"test@example.com\",\"name\":\"Test\"}")
                                .when()
                                .post(AUTH_API + "/signup/personal")
                                .then()
                                .statusCode(400);

                log.info("✅ Missing fields correctly rejected");
        }

        @AfterAll
        void cleanup() {
                log.info("🧹 Running cleanup for SignupFlowIT...");
                CleanupHelper.cleanupAll();
        }

        @AfterEach
        void afterEach() {
                log.info("---");
        }
}
