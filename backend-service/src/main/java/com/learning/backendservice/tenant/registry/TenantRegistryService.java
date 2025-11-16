package com.learning.backendservice.tenant.registry;

import com.learning.common.dto.TenantDbInfo;

public interface TenantRegistryService {
    TenantDbInfo load(String tenantId);
}
