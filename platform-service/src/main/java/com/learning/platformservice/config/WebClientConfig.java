package com.learning.platformservice.config;

import com.learning.common.infra.config.ServicesProperties;
import com.learning.common.infra.http.HttpClientFactory;
import com.learning.common.infra.log.ExchangeLoggingFilter;
import com.learning.common.infra.security.PermissionEvaluator;
import com.learning.common.infra.security.RemotePermissionEvaluator;
import com.learning.common.infra.security.RoleLookupService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ServicesProperties.class)
public class WebClientConfig {

    /**
     * Internal WebClient.Builder for service discovery + load balancing.
     */
    @Bean(name = "internalWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder internalWebClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClientFactory.httpClient()))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }

    /**
     * External WebClient.Builder for third-party calls.
     */
    @Bean(name = "externalWebClientBuilder")
    public WebClient.Builder externalWebClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClientFactory.httpClient()))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }

    /**
     * Backend-Service WebClient (internal S2S)
     */
    @Bean
    public WebClient backendWebClient(
            @Qualifier("internalWebClientBuilder") WebClient.Builder builder,
            ServicesProperties props) {
        return builder
                .baseUrl(props.getBackend().getBaseUrl())
                .filter(ExchangeLoggingFilter.logRequest())
                .filter(ExchangeLoggingFilter.logResponse())
                .build();
    }

    /**
     * Auth-Service WebClient (internal S2S)
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

    /**
     * Payment-Service WebClient (internal S2S)
     */
    @Bean
    public WebClient paymentWebClient(
            @Qualifier("internalWebClientBuilder") WebClient.Builder builder,
            ServicesProperties props) {
        return builder
                .baseUrl(props.getPayment().getBaseUrl())
                .filter(ExchangeLoggingFilter.logRequest())
                .filter(ExchangeLoggingFilter.logResponse())
                .build();
    }

    /**
     * Permission evaluator that calls auth-service for DB-backed permission checks.
     * Uses RoleLookupService for role-based access instead of X-Role header.
     */
    @Bean
    public PermissionEvaluator permissionEvaluator(WebClient authWebClient, RoleLookupService roleLookupService) {
        return new RemotePermissionEvaluator(authWebClient, roleLookupService);
    }
}
