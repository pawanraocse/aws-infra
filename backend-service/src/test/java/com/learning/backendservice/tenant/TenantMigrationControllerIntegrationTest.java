package com.learning.backendservice.tenant;

import com.learning.backendservice.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@AutoConfigureMockMvc
class TenantMigrationControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runMigrations_shouldApplySchemaToTenantDatabase() throws Exception {
        // 1. Create a dedicated tenant database within the Testcontainer
        String tenantDbName = "tenant_test_db";
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + tenantDbName);
        }

        // 2. Construct JDBC URL for the new tenant DB
        String tenantJdbcUrl = postgres.getJdbcUrl().replace("/test", "/" + tenantDbName);

        // 3. Call the migration endpoint
        String requestBody = String.format("""
                {
                    "jdbcUrl": "%s",
                    "username": "%s",
                    "password": "%s"
                }
                """, tenantJdbcUrl, postgres.getUsername(), postgres.getPassword());

        mockMvc.perform(post("/internal/tenants/test-tenant/migrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.migrationsExecuted").value(1))
                .andExpect(jsonPath("$.lastVersion").value("1"));

        // 4. Verify table exists in tenant DB
        try (Connection conn = DriverManager.getConnection(tenantJdbcUrl, postgres.getUsername(),
                postgres.getPassword());
                Statement stmt = conn.createStatement()) {
            var rs = stmt
                    .executeQuery("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'entries')");
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1), "entries table should exist");

            // Verify history table
            var rsHist = stmt.executeQuery(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'test-tenant_schema_history')");
            assertTrue(rsHist.next());
            assertTrue(rsHist.getBoolean(1), "history table should exist");
        }
    }
}
