package com.learning.platformservice.tenant.exception;

import lombok.Getter;

@Getter
public class TenantProvisioningException extends RuntimeException {
    private final String tenantId;

    public TenantProvisioningException(String tenantId, String message, Throwable cause) {
        super(message, cause);
        this.tenantId = tenantId;
    }

    public TenantProvisioningException(String tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }
}

