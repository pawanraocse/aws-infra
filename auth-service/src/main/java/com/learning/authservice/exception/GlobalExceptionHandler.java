package com.learning.authservice.exception;

import com.learning.common.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(AuthLoginException.class)
    public ResponseEntity<ErrorResponse> handleAuthLogin(AuthLoginException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getCode()) {
            case "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
            case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return buildAndLog(status, ex.getCode(), ex.getMessage(), request, ex);
    }

    @ExceptionHandler(AuthSignupException.class)
    public ResponseEntity<ErrorResponse> handleAuthSignup(AuthSignupException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getCode()) {
            case "USER_EXISTS" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return buildAndLog(status, ex.getCode(), ex.getMessage(), request, ex);
    }

    // Utility method
    private ResponseEntity<ErrorResponse> buildAndLog(HttpStatus status, String code, String message, HttpServletRequest request, Exception ex) {
        String requestId = headerOrGenerate(request, REQUEST_ID_HEADER);
        String path = request.getRequestURI();
        ErrorResponse body = ErrorResponse.of(status.value(), code, sanitize(message), requestId, path);
        log.warn("error code={} status={} path={} requestId={} message={} exception={}", code, status.value(), path, requestId, message, ex.getClass().getSimpleName());
        return ResponseEntity.status(status).body(body);
    }

    private String headerOrGenerate(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return (v == null || v.isBlank()) ? UUID.randomUUID().toString() : v;
    }

    private String sanitize(String msg) {
        if (msg == null) return "";
        return msg.replace('"', ' ');
    }
}
