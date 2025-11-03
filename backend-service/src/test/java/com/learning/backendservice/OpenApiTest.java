package com.learning.backendservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeOpenApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.info.title").value("Backend Service API"))
                .andExpect(jsonPath("$.info.version").exists());
    }

    @Test
    void shouldServeSwaggerUI() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html"));
    }

    @Test
    void shouldDocumentEntryEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/v1/entries.post").exists())
                .andExpect(jsonPath("$.paths./api/v1/entries.get").exists())
                .andExpect(jsonPath("$.paths./api/v1/entries/{id}.get").exists())
                .andExpect(jsonPath("$.paths./api/v1/entries/{id}.put").exists())
                .andExpect(jsonPath("$.paths./api/v1/entries/{id}.delete").exists());
    }

    @Test
    void shouldDocumentRequestAndResponseSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.EntryRequestDto").exists())
                .andExpect(jsonPath("$.components.schemas.EntryResponseDto").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
    }

    @Test
    void shouldDocumentValidationRules() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.EntryRequestDto.properties.key.maxLength").value(255))
                .andExpect(jsonPath("$.components.schemas.EntryRequestDto.required").isArray());
    }
}
