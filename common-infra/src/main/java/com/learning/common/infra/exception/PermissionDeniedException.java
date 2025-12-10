package com.learning.common.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception for permission/access denied scenarios.
 * Used by @RequirePermission aspect when user lacks required permission.
 * This replaces Spring Security's AccessDeniedException to avoid
 * pulling in spring-security dependency for services that don't need it.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
