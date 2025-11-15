package com.learning.platformservice.tenant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform")
public class PlatformTenantProperties {
    private boolean dbPerTenantEnabled = false;
    private boolean tenantDatabaseModeEnabled = false;
    private boolean dropOnFailure = false;
    private String tenantDbHost = "localhost";
    private int tenantDbPort = 5432;
    private String adminDatabase = "postgres";
    private String jdbcDriver = "org.postgresql.Driver";
}
