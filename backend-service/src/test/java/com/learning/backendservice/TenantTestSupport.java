package com.learning.backendservice;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


@Configuration
public class TenantTestSupport {
    @Bean
    MockMvcBuilderCustomizer tenantHeaderCustomizer() {
        return builder -> builder.defaultRequest(get("/").header("X-Tenant-Id", "tenant123"));
    }
}

