package com.learning.backendservice.config;

import com.learning.backendservice.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantContextFilterTest extends BaseControllerTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @TestConfiguration
    static class NoDefaultTenantConfig {
        @Bean
        MockMvcBuilderCustomizer noTenantHeaderCustomizer() {
            return b -> b.defaultRequest(get("/")); // no tenant header injected
        }
    }

    @Test
    @DisplayName("403 TENANT_MISSING when tenant header absent")
    void missingTenantHeader() throws Exception {
        mockMvc.perform(
                get("/api/v1/entries")
                        .accept(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISSING"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Tenant header required"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("400 TENANT_INVALID_FORMAT when tenant header invalid")
    void invalidTenantHeader() throws Exception {
        mockMvc.perform(
                get("/api/v1/entries")
                        .header(TENANT_HEADER, "!!bad!!")
                        .accept(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_INVALID_FORMAT"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Tenant ID format invalid"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("200 OK when tenant header valid")
    void validTenantHeader() throws Exception {
        mockMvc.perform(
                get("/api/v1/entries")
                        .header(TENANT_HEADER, "tenant123")
                        .accept(MediaType.APPLICATION_JSON)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }
}
