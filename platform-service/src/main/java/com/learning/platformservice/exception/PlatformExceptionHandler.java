package com.learning.platformservice.exception;

import com.learning.common.error.ErrorResponse;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformExceptionHandler {

    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTenantAlreadyExistsException(TenantAlreadyExistsException ex,
            HttpServletRequest request) {
        log.warn("Tenant already exists: {}", ex.getMessage());
        return new ResponseEntity<>(ErrorResponse.of(HttpStatus.CONFLICT.value(), "TENANT_CONFLICT",
                ex.getMessage(), request.getHeader("X-Request-Id"), request.getRequestURI()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFoundException(TenantNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Tenant not found: {}", ex.getMessage());
        return new ResponseEntity<>(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "TENANT_NOT_FOUND", ex.getMessage(),
                request.getHeader("X-Request-Id"), request.getRequestURI()), HttpStatus.NOT_FOUND);
    }
}
