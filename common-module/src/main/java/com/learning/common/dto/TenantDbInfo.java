package com.learning.common.dto;

public record TenantDbInfo(
        String jdbcUrl,
        String username,
        String password
) {}
