package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.TenantDto;

import java.util.List;
import java.util.Optional;

public interface TenantService {
    Optional<TenantDto> getTenant(String id);

    /**
     * Get all tenants in the system.
     * For super-admin platform management.
     */
    List<TenantDto> getAllTenants();
}
