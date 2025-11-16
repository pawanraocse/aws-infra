package com.learning.platformservice.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class TestWebClientOverrideConfig {

    @Bean(name = "internalWebClientBuilder")
    @Primary
    public WebClient.Builder internalWebClientBuilderTestOverride() {
        System.out.println(">>> TEST WebClient.Builder OVERRIDE LOADED <<<");

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector());
    }
}
