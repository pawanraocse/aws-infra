package com.learning.common.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Shared WebClient configuration.
 * Provides default builders if not defined by the consuming service.
 */
@Configuration
public class WebClientConfiguration {

    @Bean(name = "internalWebClientBuilder")
    @ConditionalOnMissingBean(name = "internalWebClientBuilder")
    public WebClient.Builder internalWebClientBuilder() {
        return WebClient.builder();
    }
}
