package com.learning.backendservice.tenant.registry;

import com.learning.common.dto.TenantDbConfig;

public interface TenantRegistryService {
    TenantDbConfig load(String tenantId);
}
