package com.learning.common.infra.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

@Slf4j
public class ExchangeLoggingFilter {

    public static ExchangeFilterFunction logRequest() {
        return (request, next) -> {
            log.debug("HTTP Request: {} {}", request.method(), request.url());
            return next.exchange(request);
        };
    }

    public static ExchangeFilterFunction logResponse() {
        return (request, next) ->
                next.exchange(request)
                        .doOnNext(response ->
                                log.debug("HTTP Response: {} {}", response.statusCode(), request.url()));
    }
}

