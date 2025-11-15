package com.learning.platformservice.tenant.api;

import com.learning.platformservice.test.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for DATABASE storage mode provisioning (db-per-tenant).
 * Requires flags enabled; asserts ACTIVE status and jdbcUrl without currentSchema parameter.
 */
class TenantControllerDatabaseModeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final String CONTEXT_PATH = "/platform";

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
    }
}

