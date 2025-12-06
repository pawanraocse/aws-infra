package com.learning.systemtests.auth;

import com.learning.common.dto.OrganizationSignupRequest;
import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.SignupResponse;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * System-level integration tests for signup flows.
 * Tests complete user journey: signup → tenant provisioning → Cognito user
 * creation.
 * 
 * Prerequisites:
 * - Gateway service running on localhost:8080
 * - Auth service running on localhost:8081
 * - Platform service running on localhost:8083
 * - Cognito user pool configured
 * - Database available
 * 
 * Run with: mvn verify -Psystem-tests
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignupFlowIT {

    private static final String AUTH_SERVICE_BASE_URL = "http://localhost:8081";

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = AUTH_SERVICE_BASE_URL;

        System.out.println("🧪 System Test Setup Complete");
        System.out.println("   Auth Service: " + AUTH_SERVICE_BASE_URL);
        System.out.println("   Platform Service: expected at localhost:8083");
    }

    @Test
    @Order(1)
    @DisplayName("B2C Personal Signup - Success Flow")
    void testPersonalSignupSuccess() {
        // Arrange
        String testEmail = "test.user." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String testPassword = "TestPassword123!";
        String testName = "Test User";

        PersonalSignupRequest request = new PersonalSignupRequest(
                testEmail,
                testPassword,
                testName);

        // Act & Assert
        Response response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/signup/personal")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("tenantId", notNullValue())
                .body("tenantId", startsWith("user-"))
                .body("message", containsStringIgnoringCase("successful"))
                .extract().response();

        SignupResponse signupResponse = response.as(SignupResponse.class);
        System.out.println("✅ Personal signup successful: " + signupResponse.tenantId());

        // TODO: Verify Cognito user created (requires AWS SDK or admin API)
        // TODO: Verify tenant in platform database
    }

    @Test
    @Order(2)
    @DisplayName("B2B Organization Signup - Success Flow")
    void testOrganizationSignupSuccess() {
        // Arrange
        String testEmail = "admin." + UUID.randomUUID().toString().substring(0, 8) + "@testcorp.com";
        String testPassword = "AdminPassword123!";
        String testName = "Admin User";
        String companyName = "Test Corporation " + UUID.randomUUID().toString().substring(0, 4);

        OrganizationSignupRequest request = new OrganizationSignupRequest(
                companyName,
                testEmail,
                testPassword,
                testName,
                "STANDARD");

        // Act & Assert
        Response response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/signup/organization")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("tenantId", notNullValue())
                .body("message", containsStringIgnoringCase("successful"))
                .extract().response();

        SignupResponse signupResponse = response.as(SignupResponse.class);
        assertThat(signupResponse.tenantId()).matches("[a-z0-9_-]+");

        System.out.println("✅ Organization signup successful: " + signupResponse.tenantId());
    }

    @Test
    @Order(3)
    @DisplayName("Duplicate Email - Should Return 400/409")
    void testDuplicateEmailSignup() {
        // Arrange
        String testEmail = "duplicate." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String testPassword = "TestPassword123!";
        String testName = "Duplicate User";

        PersonalSignupRequest request = new PersonalSignupRequest(testEmail, testPassword, testName);

        // First signup - should succeed
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/signup/personal")
                .then()
                .statusCode(201);

        // Second signup with same email - should fail
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/signup/personal")
                .then()
                .statusCode(anyOf(is(400), is(409)))
                .body("success", equalTo(false))
                .body("message", containsStringIgnoringCase("exist"));

        System.out.println("✅ Duplicate email correctly rejected");
    }

    @Test
    @Order(4)
    @DisplayName("Invalid Password - Should Return 400")
    void testInvalidPasswordSignup() {
        // Arrange
        String testEmail = "weakpass." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String weakPassword = "123"; // Too weak
        String testName = "Test User";

        PersonalSignupRequest request = new PersonalSignupRequest(testEmail, weakPassword, testName);

        // Act & Assert
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/signup/personal")
                .then()
                .statusCode(400);

        System.out.println("✅ Weak password correctly rejected");
    }

    @Test
    @Order(5)
    @DisplayName("Missing Required Fields - Should Return 400")
    void testMissingFieldsSignup() {
        // Act & Assert - Missing password
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"test@example.com\",\"name\":\"Test\"}")
                .when()
                .post("/auth/signup/personal")
                .then()
                .statusCode(400);

        System.out.println("✅ Missing fields correctly rejected");
    }

    @AfterEach
    void afterEach() {
        System.out.println("---");
    }
}
