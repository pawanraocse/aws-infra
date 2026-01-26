package com.learning.authservice.exception;

import com.learning.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class AuthGlobalExceptionHandler {

    @ExceptionHandler(AuthLoginException.class)
    public ResponseEntity<ErrorResponse> handleAuthLogin(AuthLoginException ex, HttpServletRequest request) {
        log.warn("Login failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(AuthSignupException.class)
    public ResponseEntity<ErrorResponse> handleAuthSignup(AuthSignupException ex, HttpServletRequest request) {
        log.warn("Signup failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request);
    }

    // Generic RuntimeException is now handled by common-infra
    // GlobalExceptionHandler
    // @ExceptionHandler(RuntimeException.class)
    // public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex,
    // HttpServletRequest request) { ... }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.of(
                status.value(),
                code,
                message,
                request.getHeader("X-Request-Id"),
                request.getRequestURI());
        return new ResponseEntity<>(errorResponse, status);
    }
}
