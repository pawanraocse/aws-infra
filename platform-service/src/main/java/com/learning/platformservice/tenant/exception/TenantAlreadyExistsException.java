package com.learning.platformservice.tenant.exception;

import lombok.Getter;

@Getter
public class TenantAlreadyExistsException extends RuntimeException {
    private final String tenantId;

    public TenantAlreadyExistsException(String tenantId) {
        super("Tenant already exists: " + tenantId);
        this.tenantId = tenantId;
    }
}

