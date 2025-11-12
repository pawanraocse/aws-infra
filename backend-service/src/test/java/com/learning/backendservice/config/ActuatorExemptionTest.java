package com.learning.backendservice.config;

import com.learning.backendservice.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class ActuatorExemptionTest extends BaseControllerTest {

    @Test
    @DisplayName("/actuator/health accessible without tenant header")
    void healthEndpointAccessibleWithoutTenant() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/info accessible without tenant header")
    void infoEndpointAccessibleWithoutTenant() throws Exception {
        mockMvc.perform(get("/actuator/info").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}

