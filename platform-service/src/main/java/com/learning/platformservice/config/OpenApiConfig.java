package com.learning.platformservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI platformOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Platform Service API")
                        .version("v1")
                        .description("Tenant lifecycle, provisioning, policy, internal token APIs"));
    }
}

