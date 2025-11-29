package com.learning.backendservice.config;

import com.learning.common.infra.http.HttpClientFactory;
import com.learning.common.infra.log.ExchangeLoggingFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Internal WebClient.Builder for calling other microservices
     * via Eureka/Kubernetes with load balancing.
     */
    @Bean(name = "internalWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder internalWebClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClientFactory.httpClient()))
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }

    /**
     * External WebClient.Builder for third-party HTTP APIs.
     * No load balancing, no discovery.
     */
    @Bean(name = "externalWebClientBuilder")
    public WebClient.Builder externalWebClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClientFactory.httpClient()))
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }

    /**
     * Platform-service WebClient (internal service-to-service call).
     * Uses the load-balanced internal builder.
     */
    @Bean
    public WebClient platformWebClient(
            @Qualifier("internalWebClientBuilder") WebClient.Builder builder,
            ServicesProperties props) {
        return builder
                .baseUrl(props.getPlatform().getBaseUrl())
                .filter(ExchangeLoggingFilter.logRequest())
                .filter(ExchangeLoggingFilter.logResponse())
                .build();
    }

    /**
     * Auth-service WebClient (internal service-to-service call).
     */
    @Bean
    public WebClient authWebClient(
            @Qualifier("internalWebClientBuilder") WebClient.Builder builder,
            ServicesProperties props) {
        return builder
                .baseUrl(props.getAuth().getBaseUrl())
                .filter(ExchangeLoggingFilter.logRequest())
                .filter(ExchangeLoggingFilter.logResponse())
                .build();
    }
}
