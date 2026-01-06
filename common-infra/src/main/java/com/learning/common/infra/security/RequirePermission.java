package com.learning.common.infra.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce permission-based access control on methods.
 * 
 * <p>
 * Basic usage (RBAC only):
 * {@code @RequirePermission(resource = "entry", action = "read")}
 * 
 * <p>
 * Resource-level usage (OpenFGA):
 * {@code @RequirePermission(resource = "document", action = "edit", resourceIdParam = "id")}
 * When resourceIdParam is specified, the aspect will extract the resource ID
 * from the
 * method parameter with that name and check OpenFGA for fine-grained access.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /**
     * The resource type being accessed (e.g., "entry", "document", "folder")
     */
    String resource();

    /**
     * The action being performed (e.g., "read", "edit", "delete", "share")
     */
    String action();

    /**
     * Optional: Name of the method parameter containing the resource ID.
     * When specified, enables OpenFGA resource-level permission checks.
     * Example: @RequirePermission(resource = "document", action = "edit",
     * resourceIdParam = "id")
     */
    String resourceIdParam() default "";
}
