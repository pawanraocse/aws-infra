package com.learning.platformservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class BackendClientConfig {

    @Bean
    public WebClient backendWebClient(@Value("${platform.backend.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(
                        new ReactorClientHttpConnector(
                                HttpClient.create()
                                        .responseTimeout(Duration.ofSeconds(10))
                                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                                        .doOnConnected(conn ->
                                                conn.addHandlerLast(new ReadTimeoutHandler(10))
                                                        .addHandlerLast(new WriteTimeoutHandler(10))
                                        )
                        )
                )
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // You can add logging here if needed
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            // You can add logging here if needed
            return Mono.just(clientResponse);
        });
    }
}
