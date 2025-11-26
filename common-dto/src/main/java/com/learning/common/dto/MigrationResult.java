package com.learning.common.dto;

/**
 * Unified DTO for tenant migration results across all services.
 * Used by platform-service, backend-service, and auth-service.
 */
public record MigrationResult(
        boolean success,
        int migrationsExecuted,
        String lastVersion) {
    /**
     * Factory method for platform-service usage (simple version response)
     */
    public static MigrationResult ofVersion(String version) {
        return new MigrationResult(true, 1, version);
    }

    /**
     * Factory method for error cases
     */
    public static MigrationResult failure() {
        return new MigrationResult(false, 0, null);
    }
}
