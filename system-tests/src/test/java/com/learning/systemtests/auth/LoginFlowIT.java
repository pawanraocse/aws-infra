package com.learning.systemtests.auth;

import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.SignupResponse;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the login flow, specifically tenant lookup
 * functionality.
 * 
 * <p>
 * These tests verify that after a user signs up:
 * </p>
 * <ul>
 * <li>The tenant lookup API returns the correct tenant</li>
 * <li>Unknown emails return empty tenant lists</li>
 * <li>The platformWebClient correctly calls platform-service at
 * /platform/internal/...</li>
 * </ul>
 * 
 * <p>
 * Prerequisites:
 * </p>
 * <ul>
 * <li>Gateway service running on localhost:8080</li>
 * <li>Auth service running on localhost:8081</li>
 * <li>Platform service running on localhost:8083</li>
 * </ul>
 * 
 * <p>
 * Run with: mvn verify -Psystem-tests -pl system-tests
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginFlowIT {

    private static final String GATEWAY_BASE_URL = "http://localhost:8080";
    private static final String AUTH_PATH = "/auth/api/v1/auth";

    // Store test data for sequential tests
    private String testEmail;
    private String testTenantId;

    @BeforeAll
    void setUp() {
        System.out.println("🧪 LoginFlowIT Setup Complete");
        System.out.println("   Gateway: " + GATEWAY_BASE_URL);
    }

    @Test
    @Order(1)
    @DisplayName("Signup creates tenant and tenant lookup returns it")
    void testSignupAndTenantLookup() {
        // Arrange - Create a unique test user
        testEmail = "login.test." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String testPassword = "SecurePassword123!";
        String testName = "Login Test User";

        PersonalSignupRequest signupRequest = new PersonalSignupRequest(
                testEmail,
                testPassword,
                testName);

        // Act 1: Signup
        Response signupResponse = given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .body(signupRequest)
                .when()
                .post(AUTH_PATH + "/signup/personal")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("tenantId", notNullValue())
                .extract().response();

        SignupResponse signup = signupResponse.as(SignupResponse.class);
        testTenantId = signup.tenantId();
        System.out.println("✅ Signup successful: " + testEmail + " -> " + testTenantId);

        // Act 2: Immediately lookup tenants for this email
        Response lookupResponse = given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .queryParam("email", testEmail)
                .when()
                .get(AUTH_PATH + "/lookup")
                .then()
                .statusCode(200)
                .body("email", equalTo(testEmail))
                .body("tenants", hasSize(1))
                .body("tenants[0].tenantId", equalTo(testTenantId))
                .body("tenants[0].tenantType", equalTo("PERSONAL"))
                .body("tenants[0].owner", equalTo(true))
                .body("defaultTenantId", equalTo(testTenantId))
                .extract().response();

        System.out.println("✅ Tenant lookup successful - tenant found: " + testTenantId);
    }

    @Test
    @Order(2)
    @DisplayName("Tenant lookup for unknown email returns empty list")
    void testTenantLookupUnknownEmail() {
        // Arrange
        String unknownEmail = "unknown." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        // Act & Assert
        given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .queryParam("email", unknownEmail)
                .when()
                .get(AUTH_PATH + "/lookup")
                .then()
                .statusCode(200)
                .body("email", equalTo(unknownEmail))
                .body("tenants", hasSize(0))
                .body("requiresSelection", equalTo(false))
                .body("defaultTenantId", nullValue());

        System.out.println("✅ Unknown email correctly returns empty tenant list");
    }

    @Test
    @Order(3)
    @DisplayName("Tenant lookup validates email format")
    void testTenantLookupInvalidEmail() {
        // Act & Assert
        given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .queryParam("email", "not-an-email")
                .when()
                .get(AUTH_PATH + "/lookup")
                .then()
                .statusCode(400);

        System.out.println("✅ Invalid email format correctly rejected");
    }

    @Test
    @Order(4)
    @DisplayName("Tenant lookup with existing email returns correct tenant info")
    void testTenantLookupReturnsCorrectTenantInfo() {
        // Skip if previous test didn't run
        Assumptions.assumeTrue(testEmail != null, "Requires testSignupAndTenantLookup to run first");

        // Act & Assert
        Response response = given()
                .baseUri(GATEWAY_BASE_URL)
                .contentType(ContentType.JSON)
                .queryParam("email", testEmail)
                .when()
                .get(AUTH_PATH + "/lookup")
                .then()
                .statusCode(200)
                .body("tenants[0].tenantName", notNullValue())
                .body("tenants[0].roleHint", equalTo("owner"))
                .body("tenants[0].ssoEnabled", equalTo(false))
                .extract().response();

        // Verify tenant name contains email or workspace
        String tenantName = response.path("tenants[0].tenantName");
        assertThat(tenantName).isNotNull();
        assertThat(tenantName.toLowerCase()).containsAnyOf("workspace", testEmail.split("@")[0]);

        System.out.println("✅ Tenant info verified: " + tenantName);
    }

    @AfterEach
    void afterEach() {
        System.out.println("---");
    }
}
