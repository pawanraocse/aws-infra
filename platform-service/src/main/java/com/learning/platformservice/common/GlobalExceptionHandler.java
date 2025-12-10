package com.learning.platformservice.common;

import com.learning.common.constants.ErrorCodes;
import com.learning.common.error.ErrorResponse;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantNotFoundException;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(TenantAlreadyExistsException.class)
        public ResponseEntity<ErrorResponse> tenantExists(TenantAlreadyExistsException ex, HttpServletRequest req) {
                log.warn("tenant_exists tenantId={}", ex.getTenantId());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), ErrorCodes.TENANT_CONFLICT.name(),
                                                ex.getMessage(), requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler(TenantProvisioningException.class)
        public ResponseEntity<ErrorResponse> provisioning(TenantProvisioningException ex, HttpServletRequest req) {
                log.error("tenant_provisioning_error tenantId={} error={}", ex.getTenantId(), ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "PROVISION_ERROR",
                                                ex.getMessage(), requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> accessDenied(org.springframework.security.access.AccessDeniedException ex,
                        HttpServletRequest req) {
                log.warn("access_denied error={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", "Access denied",
                                                requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler(com.learning.common.infra.exception.PermissionDeniedException.class)
        public ResponseEntity<ErrorResponse> permissionDenied(
                        com.learning.common.infra.exception.PermissionDeniedException ex,
                        HttpServletRequest req) {
                log.warn("permission_denied path={} error={}", req.getRequestURI(), ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", ex.getMessage(),
                                                requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
                Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                                .collect(Collectors.toMap(f -> f.getField(),
                                                f -> f.getDefaultMessage() == null ? "Invalid"
                                                                : f.getDefaultMessage()));
                log.warn("validation_error fields={}", fieldErrors);
                return ResponseEntity.badRequest()
                                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                                                ErrorCodes.TENANT_INVALID_FORMAT.name(), "Invalid request",
                                                requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> constraint(ConstraintViolationException ex, HttpServletRequest req) {
                Map<String, String> violations = ex.getConstraintViolations().stream()
                                .collect(Collectors.toMap(v -> v.getPropertyPath().toString(), v -> v.getMessage()));
                log.warn("constraint_violation violations={}", violations);
                return ResponseEntity.badRequest()
                                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                                                ErrorCodes.TENANT_INVALID_FORMAT.name(), "Constraint violation",
                                                requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
        public ResponseEntity<ErrorResponse> illegal(RuntimeException ex, HttpServletRequest req) {
                log.warn("request_invalid error={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                HttpStatus.BAD_REQUEST.value(),
                                                ErrorCodes.TENANT_INVALID_FORMAT.name(),
                                                ex.getMessage(),
                                                requestId(req),
                                                req.getRequestURI()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest req) {
                log.error("unexpected_error error={}", ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                ErrorCodes.INTERNAL_ERROR.name(), "An unexpected error occurred",
                                                requestId(req), req.getRequestURI()));
        }

        @ExceptionHandler(TenantNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException ex,
                        HttpServletRequest request) {
                ErrorResponse error = new ErrorResponse(
                                Instant.now(),
                                404,
                                "NOT_FOUND",
                                ex.getMessage(),
                                "NA",
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        private String requestId(HttpServletRequest req) {
                String rid = req.getHeader("X-Request-Id");
                return rid != null ? rid : "NA";
        }
}
