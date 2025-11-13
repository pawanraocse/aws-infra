package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;

import java.util.Optional;

public interface TenantService {
    TenantDto provisionTenant(ProvisionTenantRequest request);

    Optional<TenantDto> getTenant(String id);
}

