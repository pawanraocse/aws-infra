package com.learning.common.infra.openfga;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for OpenFGA integration.
 * Enable OpenFGA by setting openfga.enabled=true in application.yml
 * 
 * Example:
 * openfga:
 * enabled: true
 * api-url: http://localhost:8090
 * store-id: ${OPENFGA_STORE_ID:}
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openfga")
public class OpenFgaProperties {

    /**
     * Enable/disable OpenFGA integration.
     * When disabled, only RBAC (@RequirePermission) is used.
     */
    private boolean enabled = false;

    /**
     * OpenFGA API URL (e.g., http://localhost:8090)
     */
    private String apiUrl = "http://localhost:8090";

    /**
     * OpenFGA Store ID (OPTIONAL - for dev/fallback only).
     * In production, store ID is looked up per-tenant from
     * TenantDbConfig.fgaStoreId.
     * Only used by OpenFgaStoreService.getOrCreateDevStore() for local testing.
     */
    private String storeId;

    /**
     * Authorization model ID (optional).
     * If not set, uses the latest model in the store.
     */
    private String authorizationModelId;

    /**
     * Connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * Read timeout in milliseconds.
     */
    private int readTimeoutMs = 5000;
}
