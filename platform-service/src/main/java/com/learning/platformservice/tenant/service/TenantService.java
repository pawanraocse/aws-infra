package com.learning.platformservice.tenant.service;

import com.learning.platformservice.tenant.dto.TenantDto;

import java.util.Optional;

public interface TenantService {
    Optional<TenantDto> getTenant(String id);
}
