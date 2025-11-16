package com.learning.platformservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BackendClientConfig {

    @Bean
    public WebClient backendWebClient(@Value("${platform.backend.base-url:http://localhost:8082}") String backendBaseUrl) {
        return WebClient.builder()
                .baseUrl(backendBaseUrl)
                .build();
    }
}

