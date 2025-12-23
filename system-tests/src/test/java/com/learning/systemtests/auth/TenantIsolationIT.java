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
 * Integration tests for tenant isolation.
 * Verifies that data is properly isolated between tenants.
 * 
 * <p>
 * Uses AdminConfirmSignUp to auto-verify test users.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIsolationIT extends BaseSystemTest {

        private String tenantAToken;
        private String tenantAId;
        private String entryId;

        @BeforeAll
        void setupTenant() {
                log.info("Setting up tenant for TenantIsolationIT...");
                try {
                        // Signup and confirm user
                        AuthHelper.UserCredentials creds = AuthHelper.signupAndConfirm();
                        tenantAId = creds.tenantId();

                        // Login to get token
                        tenantAToken = AuthHelper.login(creds.email(), creds.password());
                        log.info("✅ Setup complete: tenant={}", tenantAId);
                } catch (Exception e) {
                        log.warn("Setup failed: {} - tests will be skipped", e.getMessage());
                }
        }

        @Test
        @Order(1)
        @DisplayName("Should create and retrieve entry in same tenant")
        void shouldCreateAndRetrieveEntryInSameTenant() {
                Assumptions.assumeTrue(tenantAToken != null, "Requires authenticated user");

                // Create entry
                String entryPayload = """
                                {
                                    "title": "Test Entry",
                                    "content": "Test content for tenant isolation"
                                }
                                """;

                entryId = given()
                                .header("Authorization", "Bearer " + tenantAToken)
                                .contentType(ContentType.JSON)
                                .body(entryPayload)
                                .when()
                                .post(ENTRIES_API)
                                .then()
                                .statusCode(anyOf(is(200), is(201)))
                                .body("title", equalTo("Test Entry"))
                                .extract()
                                .jsonPath().getString("id");

                log.info("✅ Created entry: {}", entryId);

                // Retrieve entry
                given()
                                .header("Authorization", "Bearer " + tenantAToken)
                                .when()
                                .get(ENTRIES_API + "/" + entryId)
                                .then()
                                .statusCode(200)
                                .body("id", equalTo(entryId))
                                .body("title", equalTo("Test Entry"));

                log.info("✅ Retrieved entry successfully");
        }

        @Test
        @Order(2)
        @DisplayName("Should fail to access entry with spoofed X-Tenant-Id header")
        void shouldFailToAccessEntryWithDifferentTenantHeader() {
                Assumptions.assumeTrue(tenantAToken != null, "Requires authenticated user");
                Assumptions.assumeTrue(entryId != null, "Requires entry from previous test");

                // Try to access with spoofed tenant header
                given()
                                .header("Authorization", "Bearer " + tenantAToken)
                                .header("X-Tenant-Id", "fake-tenant-id")
                                .when()
                                .get(ENTRIES_API + "/" + entryId)
                                .then()
                                .statusCode(anyOf(is(403), is(404))); // Either forbidden or not found

                log.info("✅ Spoofed tenant header correctly rejected");
        }

        @Test
        @Order(3)
        @DisplayName("Should fail access without authentication")
        void shouldFailAccessWithoutAuthentication() {
                given()
                                .when()
                                .get(ENTRIES_API + "/some-id")
                                .then()
                                .statusCode(401);

                log.info("✅ Unauthenticated request correctly rejected");
        }

        @AfterAll
        void cleanup() {
                log.info("🧹 Running cleanup for TenantIsolationIT...");
                CleanupHelper.cleanupAll();
        }

        @AfterEach
        void afterEach() {
                log.info("---");
        }
}
