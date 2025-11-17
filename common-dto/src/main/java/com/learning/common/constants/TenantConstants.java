package com.learning.common.constants;

import java.util.regex.Pattern;

/**
 * Tenant related constants and validation (NT-22)
 */
public final class TenantConstants {
    private TenantConstants() {}
    public static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");
    public static final String CODE_TENANT_MISSING = "TENANT_MISSING";
    public static final String CODE_TENANT_CONFLICT = "TENANT_CONFLICT";
    public static final String CODE_TENANT_INVALID_FORMAT = "TENANT_INVALID_FORMAT";
}

