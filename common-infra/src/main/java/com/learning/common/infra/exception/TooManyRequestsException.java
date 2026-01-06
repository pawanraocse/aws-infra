package com.learning.common.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user exceeds the rate limit for an API endpoint.
 * Returns HTTP 429 Too Many Requests.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }

    public TooManyRequestsException(String endpoint, String userId) {
        super(String.format("Rate limit exceeded for endpoint '%s'. Please try again later.", endpoint));
    }
}
