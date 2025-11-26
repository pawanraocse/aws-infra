package com.learning.platformservice.tenant.action.migration;

import com.learning.common.dto.MigrationResult;
import com.learning.common.dto.TenantDbConfig;

public interface ServiceMigrationStrategy {
    String serviceName();

    MigrationResult migrate(String tenantId, TenantDbConfig config);
}
