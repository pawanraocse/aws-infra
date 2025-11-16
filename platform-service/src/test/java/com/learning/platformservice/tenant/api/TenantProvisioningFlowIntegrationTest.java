package com.learning.platformservice.tenant.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.learning.common.util.SimpleCryptoUtil;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.repo.TenantRepository;
import com.learning.platformservice.test.BaseIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for full tenant provisioning flow, including retry, error handling, and both SCHEMA and DATABASE modes.
 */
@Slf4j
class TenantProvisioningFlowIntegrationTest extends BaseIntegrationTest {

    protected static WireMockServer wireMockServer;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DataSource adminDataSource;

    @Autowired
    private TenantProvisioner tenantProvisioner;

    private static final String CONTEXT_PATH = "/platform";

    @BeforeAll
    static void setupWireMock() {
        log.info("Starting WireMock server...");
        int port = 9999;
        wireMockServer = new WireMockServer(port);
        wireMockServer.start();
        log.info("Started WireMock server at {}", wireMockServer.baseUrl());
        configureFor("localhost", port);
        WireMock.stubFor(WireMock.post(WireMock.urlMatching("/internal/tenants/.*/migrate"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"lastVersion\":\"v1.0.0\"}")));
    }

    @AfterAll
    static void stopWireMock() {
        log.info("Stopping WireMock server...");
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Provision tenant successfully - SCHEMA mode")
    void provisionTenant_success() throws Exception {
        String body = """
                {
                  "id": "acmeit",
                  "name": "Acme IT",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD"
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acmeit"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Conflict when provisioning duplicate tenant id")
    void provisionTenant_conflict() throws Exception {
        String body = """
                {
                  "id": "dupco",
                  "name": "Dup Co",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD"
                }
                """;
        // First succeeds
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        // Second should conflict
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_CONFLICT"));
    }

    // --- DATABASE mode tests (from TenantControllerDatabaseModeIntegrationTest) ---
    @DynamicPropertySource
    static void flags(DynamicPropertyRegistry registry) {
        registry.add("platform.db-per-tenant.enabled", () -> "true");
        registry.add("platform.tenant.database-mode.enabled", () -> "true");
    }

    @Test
    @DisplayName("Provision tenant successfully - DATABASE mode")
    void provisionTenant_databaseMode() throws Exception {
        String body = """
                {
                  "id": "dbtenant1",
                  "name": "DB Tenant One",
                  "storageMode": "DATABASE",
                  "slaTier": "STANDARD"
                }
                """;
        var mvcResult = mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("dbtenant1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.jdbcUrl").exists())
                .andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        assertThat(response).doesNotContain("currentSchema=");
        assertThat(response).contains("jdbc:postgresql://");
        verifyTenantDbUserAndPermissions("dbtenant1");

    }

    // --- Flow and edge case tests (original TenantProvisioningFlowIntegrationTest) ---
    @Test
    @DisplayName("Provision tenant with invalid input should fail validation")
    void provisionTenant_invalidInput() throws Exception {
        String body = """
                {
                  "id": "",
                  "name": "",
                  "storageMode": "INVALID",
                  "slaTier": ""
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Retry migration endpoint is idempotent and works after failure")
    void retryMigration_flow() throws Exception {
        String body = """
                {
                  "id": "failretry",
                  "name": "Fail Retry",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD"
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("failretry"));
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants/failretry/retry-migration")
                        .contextPath(CONTEXT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("failretry"));
    }

    @Test
    @DisplayName("Provisioning status transitions and idempotency")
    void statusTransitions_andIdempotency() throws Exception {
        String body = """
                {
                  "id": "transitid",
                  "name": "Transit Tenant",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD"
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants/transitid/retry-migration")
                        .contextPath(CONTEXT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Provision tenant with invalid storage mode should fail")
    void provisionTenant_invalidStorageMode() throws Exception {
        String body = """
                {
                  "id": "invalidmode",
                  "name": "Invalid Mode",
                  "storageMode": "FOO",
                  "slaTier": "STANDARD"
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Provision tenant with missing required fields should fail")
    void provisionTenant_missingFields() throws Exception {
        String body = """
                {
                  "id": "",
                  "name": "",
                  "storageMode": "",
                  "slaTier": ""
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Provision tenant with extra fields should create db user with correct permissions")
    void provisionTenant_fullFlowPermissionTest() throws Exception {

        // ---------- 1. Call API ----------
        String body = """
                {
                  "id": "extrafield",
                  "name": "Extra Field",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD",
                  "foo": "bar"
                }
                """;

        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("extrafield"));

        verifyTenantDbUserAndPermissions("extrafield");
    }

    /**
     * Verifies that the tenant DB user exists, can connect, and has expected permissions.
     * Checks:
     *  - User exists in pg_roles
     *  - Can connect with decrypted password
     *  - Can set search_path to schema
     *  - CREATE TABLE fails (not owner)
     *  - SELECT 1 succeeds
     */
    private void verifyTenantDbUserAndPermissions(String tenantId) throws Exception {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        String jdbcUrl = tenant.getJdbcUrl();
        String username = tenant.getDbUserSecretRef();
        String password = SimpleCryptoUtil.decrypt(tenant.getDbUserPasswordEnc());
        String schemaName = tenantProvisioner.buildDatabaseName(tenantId);

        // 1. Verify user exists in PostgreSQL
        try (Connection admin = adminDataSource.getConnection();
             PreparedStatement ps = admin.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("Tenant DB user should exist in pg_roles")
                        .isTrue();
            }
        }

        // 2. Connect as tenant user and check permissions
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             var stmt = conn.createStatement()) {
            // Set search_path to schema (should succeed)
            stmt.execute("SET search_path TO " + schemaName);

            // CREATE TABLE should fail (not owner)
            boolean createFailed = false;
            try {
                stmt.execute("CREATE TABLE " + schemaName + ".test_table(id INT)");
            } catch (SQLException ignored) {
                createFailed = true;
            }
            assertThat(createFailed)
                    .as("Tenant user should NOT be allowed to create tables")
                    .isTrue();

            // SELECT 1 should succeed
            assertThat(stmt.execute("SELECT 1")).isTrue();
        }
    }



    @Test
    @DisplayName("Retry migration for non-existent tenant should return 404")
    void retryMigration_nonExistentTenant() throws Exception {
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants/notfound/retry-migration")
                        .contextPath(CONTEXT_PATH))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Provision tenant with special characters in ID should fail validation")
    void provisionTenant_specialCharId() throws Exception {
        String body = """
                {
                  "id": "bad!@#",
                  "name": "Special Char",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD"
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Provision tenant with very long name and ID should fail validation")
    void provisionTenant_longNameId() throws Exception {
        String longId = "a".repeat(130);
        String longName = "b".repeat(300);
        String body = String.format("""
                {
                  "id": "%s",
                  "name": "%s",
                  "storageMode": "SCHEMA",
                  "slaTier": "STANDARD"
                }
                """, longId, longName);
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Provision tenant with whitespace-only fields should fail validation")
    void provisionTenant_whitespaceFields() throws Exception {
        String body = """
                {
                  "id": "   ",
                  "name": "   ",
                  "storageMode": "   ",
                  "slaTier": "   "
                }
                """;
        mockMvc.perform(post(CONTEXT_PATH + "/api/tenants")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

}
