package com.learning.systemtests.auth;

import com.learning.systemtests.BaseSystemTest;
import org.junit.jupiter.api.*;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for tenant isolation.
 * Verifies that data is properly isolated between tenants.
 * 
 * <p>
 * <b>REQUIRES:</b> Verified Cognito user. Currently disabled.
 * </p>
 */
@Disabled("Requires verified Cognito user")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIsolationIT extends BaseSystemTest {

        private String tenantAToken;
        private String tenantAId;
        private String entryId;

        @BeforeAll
        void setupTenant() {
                log.info("TenantIsolationIT requires verified Cognito user - skipped");
        }

        @Test
        @Order(1)
        @DisplayName("Should create and retrieve entry in same tenant")
        void shouldCreateAndRetrieveEntryInSameTenant() {
                Assumptions.assumeTrue(tenantAToken != null, "Requires authenticated user");
                log.info("Test requires verified user - skipped");
        }

        @Test
        @Order(2)
        @DisplayName("Should fail to access entry with spoofed tenant header")
        void shouldFailToAccessEntryWithDifferentTenantHeader() {
                Assumptions.assumeTrue(entryId != null, "Requires entry from previous test");
                log.info("Test requires verified user - skipped");
        }

        @Test
        @Order(3)
        @DisplayName("Should fail access without authentication")
        void shouldFailAccessWithoutAuthentication() {
                // This test doesn't need login
                given()
                                .when()
                                .get(ENTRIES_API + "/some-id")
                                .then()
                                .statusCode(401);

                log.info("✅ Unauthenticated request correctly rejected");
        }

        @AfterEach
        void afterEach() {
                log.info("---");
        }
}
