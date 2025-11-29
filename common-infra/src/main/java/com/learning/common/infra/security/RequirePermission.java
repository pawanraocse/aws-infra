package com.learning.common.infra.security;

import java.lang.annotation.*;

/**
 * Annotation to enforce permission-based access control on methods.
 * Usage: @RequirePermission(resource = "entry", action = "read")
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /**
     * The resource being accessed (e.g., "entry", "user", "tenant")
     */
    String resource();

    /**
     * The action being performed (e.g., "read", "create", "manage")
     */
    String action();
}
