package com.learning.authservice.exception;

import java.time.Instant;

/**
 * NT-08 ErrorResponse record
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        String requestId,
        String path,
        Instant timestamp
) {
}
