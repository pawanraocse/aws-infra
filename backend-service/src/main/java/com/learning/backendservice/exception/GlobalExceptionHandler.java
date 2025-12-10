package com.learning.backendservice.exception;

import com.learning.common.constants.HeaderNames;
import com.learning.common.error.ErrorResponse;
import com.learning.common.infra.exception.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleResourceNotFound(
                        ResourceNotFoundException ex, HttpServletRequest request) {
                String requestId = request.getHeader(HeaderNames.REQUEST_ID);
                ErrorResponse error = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", ex.getMessage(),
                                requestId,
                                request.getRequestURI());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgument(
                        IllegalArgumentException ex, HttpServletRequest request) {
                String requestId = request.getHeader(HeaderNames.REQUEST_ID);
                ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", ex.getMessage(),
                                requestId, request.getRequestURI());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        @ExceptionHandler(PermissionDeniedException.class)
        public ResponseEntity<ErrorResponse> handlePermissionDenied(
                        PermissionDeniedException ex, HttpServletRequest request) {
                String requestId = request.getHeader(HeaderNames.REQUEST_ID);
                log.warn("Permission denied on path={} requestId={}: {}", request.getRequestURI(), requestId,
                                ex.getMessage());
                ErrorResponse error = ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", ex.getMessage(),
                                requestId, request.getRequestURI());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidationErrors(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {
                // keep existing validation response structure for now
                Map<String, String> errors = new HashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
                Map<String, Object> response = new HashMap<>();
                response.put("timestamp", LocalDateTime.now());
                response.put("status", HttpStatus.BAD_REQUEST.value());
                response.put("code", "VALIDATION_FAILED");
                response.put("errors", errors);
                response.put("path", request.getRequestURI());
                response.put("requestId", request.getHeader(HeaderNames.REQUEST_ID));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGenericException(
                        Exception ex, HttpServletRequest request) {
                String requestId = request.getHeader(HeaderNames.REQUEST_ID);
                log.error("Unexpected error on path={} requestId={}: {}", request.getRequestURI(), requestId,
                                ex.getMessage(),
                                ex);
                ErrorResponse error = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                                "An unexpected error occurred", requestId, request.getRequestURI());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
}
