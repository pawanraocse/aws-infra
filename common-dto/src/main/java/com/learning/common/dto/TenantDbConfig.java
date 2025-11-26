package com.learning.common.dto;

public record TenantDbConfig(
        String jdbcUrl,
        String username,
        String password
) {}
