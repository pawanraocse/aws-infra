package com.learning.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Canonical error response schema (NT-22).
 * Fields:
 *  - timestamp: RFC3339 instant
 *  - status: HTTP status code
 *  - code: application error code
 *  - message: human readable message
 *  - requestId: correlation id
 *  - path: request path
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("status") int status,
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("requestId") String requestId,
        @JsonProperty("path") String path
) {
    public static ErrorResponse of(int status, String code, String message, String requestId, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, requestId, path);
    }
}

