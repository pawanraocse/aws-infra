package com.learning.backendservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient platformWebClient(@Value("${platform.service.base-url:http://localhost:8083}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
