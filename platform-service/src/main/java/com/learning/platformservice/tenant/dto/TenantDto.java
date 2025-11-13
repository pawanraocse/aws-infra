package com.learning.platformservice.tenant.dto;

public record TenantDto(
        String id,
        String name,
        String status,
        String storageMode,
        String slaTier,
        String jdbcUrl
) {
}

