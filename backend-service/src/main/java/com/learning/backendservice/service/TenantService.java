package com.learning.backendservice.service;

import com.learning.backendservice.dto.TenantRequestDto;
import com.learning.backendservice.dto.TenantResponseDto;

public interface TenantService {
    TenantResponseDto createTenant(TenantRequestDto request);

    TenantResponseDto getTenant(String tenantId);

    void provisionTenantSchema(String tenantId);
}
