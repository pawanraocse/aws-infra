package com.learning.platformservice.tenant.provision;

import java.util.Locale;

public enum TenantStorageEnum {
    SCHEMA, DATABASE;

    public static TenantStorageEnum fromString(String mode) {
        if (mode == null) throw new IllegalArgumentException("Storage mode cannot be null");
        try {
            return TenantStorageEnum.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown storage mode: " + mode, e);
        }
    }
}
