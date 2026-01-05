package com.learning.common.dto;

/**
 * Unified DTO for tenant migration results across all services.
 * Used by platform-service, backend-service, and auth-service.
 */
public record MigrationResult(
        boolean success,
        int migrationsExecuted,
        String lastVersion,
        String fgaStoreId) {

    /**
     * Compatibility constructor for services that don't use OpenFGA (e.g.
     * backend-service).
     */
    public MigrationResult(boolean success, int migrationsExecuted, String lastVersion) {
        this(success, migrationsExecuted, lastVersion, null);
    }

    /**
     * Factory method for platform-service usage (simple version response)
     */
    public static MigrationResult ofVersion(String version) {
        return new MigrationResult(true, 1, version, null);
    }

    /**
     * Factory method for error cases
     */
    public static MigrationResult failure() {
        return new MigrationResult(false, 0, null, null);
    }
}
