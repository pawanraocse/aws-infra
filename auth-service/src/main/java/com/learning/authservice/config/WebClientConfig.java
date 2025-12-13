package com.learning.authservice.config;

import com.learning.common.infra.log.ExchangeLoggingFilter;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for inter-service communication.
 * Uses Eureka for service discovery with load balancing.
 */
@Configuration
public class WebClientConfig {

        private static final int CONNECT_TIMEOUT_MILLIS = 5000;
        private static final int READ_TIMEOUT_SECONDS = 30;
        private static final int WRITE_TIMEOUT_SECONDS = 30;

        /**
         * Load-balanced WebClient.Builder for services registered with Eureka.
         */
        @Bean
        @LoadBalanced
        public WebClient.Builder loadBalancedWebClientBuilder() {
                HttpClient httpClient = HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                                .doOnConnected(
                                                conn -> conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS,
                                                                TimeUnit.SECONDS))
                                                                .addHandlerLast(new WriteTimeoutHandler(
                                                                                WRITE_TIMEOUT_SECONDS,
                                                                                TimeUnit.SECONDS)));

                return WebClient.builder()
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .filter(ExchangeLoggingFilter.logRequest())
                                .filter(ExchangeLoggingFilter.logResponse());
        }

        /**
         * WebClient for calling platform-service via Eureka service discovery.
         * Uses service name "platform-service" registered in Eureka.
         */
        @Bean
        public WebClient platformWebClient(WebClient.Builder loadBalancedWebClientBuilder) {
                return loadBalancedWebClientBuilder
                                .baseUrl("http://platform-service/platform")
                                .build();
        }
}
