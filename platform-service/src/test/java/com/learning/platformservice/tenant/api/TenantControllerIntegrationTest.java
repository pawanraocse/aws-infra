package com.learning.platformservice.tenant.api;

import com.learning.platformservice.test.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TenantControllerIntegrationTest extends BaseIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    private static final String CONTEXT_PATH = "/platform";

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
                        .content(body)
                        // Bypass security context (resource server will likely reject without JWT; assume test profile relaxes it)
                )
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
}
