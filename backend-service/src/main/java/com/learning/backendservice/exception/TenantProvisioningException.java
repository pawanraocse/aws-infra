package com.learning.backendservice.exception;

public class TenantProvisioningException extends RuntimeException {
    public TenantProvisioningException(String message) {
        super(message);
    }

    public TenantProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
