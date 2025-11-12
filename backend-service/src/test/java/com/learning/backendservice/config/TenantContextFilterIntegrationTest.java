package com.learning.backendservice.config;

import com.learning.backendservice.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantContextFilterIntegrationTest extends BaseControllerTest {

    @Test
    @DisplayName("Should return 403 TENANT_MISSING when X-Tenant-Id header absent")
    void missingTenantHeaderReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/entries")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISSING"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("Should return 400 TENANT_INVALID_FORMAT for invalid tenant header")
    void invalidTenantFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/entries")
                        .header("X-Tenant-Id", "!!bad!!")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_INVALID_FORMAT"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("Should allow request with valid tenant header")
    void validTenantPasses() throws Exception {
        mockMvc.perform(get("/api/v1/entries")
                        .header("X-Tenant-Id", "tenant123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }
}

