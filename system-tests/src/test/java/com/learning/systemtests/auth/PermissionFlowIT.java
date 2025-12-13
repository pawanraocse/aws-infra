package com.learning.systemtests.auth;

import com.learning.common.dto.PersonalSignupRequest;
import com.learning.common.dto.SignupResponse;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for permission and role assignment flow.
 * 
 * <p>
 * These tests verify that after tenant provisioning:
 * </p>
 * <ul>
 * <li>Flyway migrations create the roles and permissions tables</li>
 * <li>Default roles are seeded in the tenant database</li>
 * <li>User-tenant membership is correctly created in platform DB</li>
 * </ul>
 * 
 * <p>
 * Prerequisites:
 * </p>
 * <ul>
 * <li>All services running via docker-compose</li>
 * <li>PostgreSQL accessible on localhost:5432</li>
 * </ul>
 * 
 * <p>
 * Run with: mvn verify -Psystem-tests -pl system-tests
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionFlowIT {

    private static final String GATEWAY_BASE_URL = "http://localhost:8080";
    private static final String AUTH_PATH = "/auth/api/v1/auth";

    // Database connection info (matches docker-compose)
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 5432;
    private static final String DB_ADMIN_USER = "postgres";
    private static final String DB_ADMIN_PASSWORD = "postgres";
    private static final String PLATFORM_DB = "awsinfra";

    // Store test data for sequential tests
    private String testEmail;
    private String testTenantId;
    private String tenantDbName;

    @BeforeAll
    void setUp() {
        System.out.println("🧪 PermissionFlowIT Setup Complete");
        System.out.println("   Gateway: " + GATEWAY_BASE_URL);
        System.out.println("   Database: " + DB_HOST + ":" + DB_PORT);
    }

    @Test
    @Order(1)
    @DisplayName("Signup creates tenant and user-tenant membership in platform DB")
    void testSignupCreatesMembership() throws Exception {
        // Arrange
        testEmail = "perm.test." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String testPassword = "SecurePassword123!";
        String testName = "Permission Test User";

        PersonalSignupRequest signupRequest = new PersonalSignupRequest(
                testEmail,
                testPassword,
                testName);

        // Act: Signup
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
        tenantDbName = "t_" + testTenantId.replace("-", "_");

        System.out.println("✅ Signup successful: " + testEmail + " -> " + testTenantId);
        System.out.println("   Tenant DB: " + tenantDbName);

        // Assert: Verify user-tenant membership exists in platform DB
        try (Connection conn = getConnection(PLATFORM_DB)) {
            String query = """
                    SELECT tenant_id, role_hint, is_owner, status
                    FROM user_tenant_memberships
                    WHERE LOWER(user_email) = LOWER(?)
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, testEmail);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertThat(rs.next())
                            .as("User-tenant membership should exist")
                            .isTrue();

                    assertThat(rs.getString("tenant_id")).isEqualTo(testTenantId);
                    assertThat(rs.getString("role_hint")).isEqualTo("owner");
                    assertThat(rs.getBoolean("is_owner")).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("ACTIVE");

                    System.out.println("✅ User-tenant membership verified in platform DB");
                }
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Tenant database has roles table after Flyway migration")
    void testTenantDbHasRolesTable() throws Exception {
        // Skip if previous test didn't run
        Assumptions.assumeTrue(tenantDbName != null, "Requires testSignupCreatesMembership to run first");

        // Assert: Verify roles table exists in tenant DB
        try (Connection conn = getConnection(tenantDbName)) {
            String query = """
                    SELECT COUNT(*) as role_count
                    FROM public.roles
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(query);
                    ResultSet rs = stmt.executeQuery()) {

                assertThat(rs.next()).isTrue();
                int roleCount = rs.getInt("role_count");

                // Should have at least the default roles from migration
                assertThat(roleCount)
                        .as("Tenant DB should have default roles after migration")
                        .isGreaterThanOrEqualTo(1);

                System.out.println("✅ Roles table exists with " + roleCount + " roles");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Tenant database has required tables after Flyway migration")
    void testTenantDbHasRequiredTables() throws Exception {
        // Skip if previous test didn't run
        Assumptions.assumeTrue(tenantDbName != null, "Requires testSignupCreatesMembership to run first");

        // Assert: Verify all required tables exist
        String[] requiredTables = { "roles", "permissions", "role_permissions", "user_roles" };

        try (Connection conn = getConnection(tenantDbName)) {
            for (String tableName : requiredTables) {
                String query = """
                        SELECT EXISTS (
                            SELECT FROM information_schema.tables
                            WHERE table_schema = 'public'
                            AND table_name = ?
                        )
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, tableName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getBoolean(1))
                                .as("Table '" + tableName + "' should exist in tenant DB")
                                .isTrue();
                    }
                }
            }
            System.out.println("✅ All required tables exist: " + String.join(", ", requiredTables));
        }
    }

    @Test
    @Order(4)
    @DisplayName("Tenant record exists in platform DB with correct config")
    void testTenantRecordExists() throws Exception {
        // Skip if previous test didn't run
        Assumptions.assumeTrue(testTenantId != null, "Requires testSignupCreatesMembership to run first");

        try (Connection conn = getConnection(PLATFORM_DB)) {
            String query = """
                    SELECT id, name, status, tenant_type, jdbc_url
                    FROM tenant
                    WHERE id = ?
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, testTenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertThat(rs.next())
                            .as("Tenant record should exist in platform DB")
                            .isTrue();

                    assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                    assertThat(rs.getString("tenant_type")).isEqualTo("PERSONAL");

                    String jdbcUrl = rs.getString("jdbc_url");
                    assertThat(jdbcUrl)
                            .as("JDBC URL should point to tenant database")
                            .contains(tenantDbName);

                    System.out.println("✅ Tenant record verified: " + testTenantId);
                    System.out.println("   JDBC URL: " + jdbcUrl);
                }
            }
        }
    }

    /**
     * Get a database connection.
     */
    private Connection getConnection(String dbName) throws Exception {
        String url = String.format("jdbc:postgresql://%s:%d/%s", DB_HOST, DB_PORT, dbName);
        return DriverManager.getConnection(url, DB_ADMIN_USER, DB_ADMIN_PASSWORD);
    }

    @AfterEach
    void afterEach() {
        System.out.println("---");
    }
}
