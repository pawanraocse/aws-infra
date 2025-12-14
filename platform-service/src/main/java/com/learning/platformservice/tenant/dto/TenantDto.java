package com.learning.platformservice.tenant.dto;

public record TenantDto(
                String id,
                String name,
                String tenantType,
                String status,
                String storageMode,
                String slaTier,
                String jdbcUrl,
                String lastMigrationVersion) {
}
