package com.learning.platformservice.tenant.action;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.entity.Tenant;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Context passed to each provision action. Actions may enrich mutable fields.
 */
@Getter
@Setter
@Accessors(chain = true)
public class TenantProvisionContext {
    private final ProvisionTenantRequest request;
    private final Tenant tenant;
    private final Instant startTime = Instant.now();

    // Mutable enrichment fields
    private String jdbcUrl;
    private String lastMigrationVersion;
    private boolean failed;
    private Exception failureCause;

    public TenantProvisionContext(ProvisionTenantRequest request, Tenant tenant) {
        this.request = request;
        this.tenant = tenant;
    }
}
