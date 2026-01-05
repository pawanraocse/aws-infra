package com.learning.common.infra.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service to provision OpenFGA stores for tenants.
 * Call this during tenant signup to create a dedicated OpenFGA store.
 * 
 * Usage in TenantProvisioner or signup flow:
 * 
 * @Autowired OpenFgaStoreService fgaStoreService;
 * 
 *            public void provisionTenant(String tenantId) {
 *            // ... existing provisioning ...
 * 
 *            // Create OpenFGA store for this tenant
 *            String fgaStoreId = fgaStoreService.createStoreForTenant(tenantId,
 *            tenantName);
 *            // Save fgaStoreId to tenant record
 *            }
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaStoreService {

    private final OpenFgaProperties properties;

    /**
     * Create a new OpenFGA store for a tenant.
     * Each tenant gets their own isolated store.
     * 
     * @param tenantId   Tenant identifier (used in store name)
     * @param tenantName Human-readable tenant name
     * @return Store ID to be saved in tenant record
     */
    public String createStoreForTenant(String tenantId, String tenantName) {
        if (!properties.isEnabled()) {
            log.debug("OpenFGA disabled, skipping store creation for tenant: {}", tenantId);
            return null;
        }

        try {
            log.info("Creating OpenFGA store for tenant: {} ({})", tenantId, tenantName);

            var configuration = new ClientConfiguration()
                    .apiUrl(properties.getApiUrl());

            var client = new OpenFgaClient(configuration);

            // Create store with tenant-specific name
            var request = new CreateStoreRequest()
                    .name(buildStoreName(tenantId, tenantName));

            var response = client.createStore(request).get();
            String storeId = response.getId();

            log.info("✅ OpenFGA store created for tenant {}: {}", tenantId, storeId);

            // Apply authorization model to the new store
            applyModelToStore(storeId);

            return storeId;

        } catch (Exception e) {
            log.error("❌ Failed to create OpenFGA store for tenant {}: {}", tenantId, e.getMessage(), e);
            // Don't fail tenant provisioning - OpenFGA is optional
            return null;
        }
    }

    /**
     * Delete OpenFGA store when tenant is deleted.
     * 
     * @param storeId Store ID from tenant record
     */
    public void deleteStoreForTenant(String storeId) {
        if (!properties.isEnabled() || storeId == null) {
            return;
        }

        try {
            log.info("Deleting OpenFGA store: {}", storeId);

            var configuration = new ClientConfiguration()
                    .apiUrl(properties.getApiUrl())
                    .storeId(storeId);

            var client = new OpenFgaClient(configuration);
            client.deleteStore().get();

            log.info("✅ OpenFGA store deleted: {}", storeId);

        } catch (Exception e) {
            log.error("Failed to delete OpenFGA store {}: {}", storeId, e.getMessage(), e);
        }
    }

    /**
     * Get or create a development store for local testing.
     * This creates a single shared store when no tenant-specific store exists.
     */
    public String getOrCreateDevStore() {
        if (!properties.isEnabled()) {
            return null;
        }

        // If a store ID is configured, use it
        if (properties.getStoreId() != null && !properties.getStoreId().isEmpty()) {
            return properties.getStoreId();
        }

        // Create a dev store
        return createStoreForTenant("dev", "Development Store");
    }

    private String buildStoreName(String tenantId, String tenantName) {
        // Store name: "saas-tenant-{tenantId}-{tenantName}"
        String safeName = tenantName != null
                ? tenantName.replaceAll("[^a-zA-Z0-9-]", "").substring(0, Math.min(20, tenantName.length()))
                : "";
        return String.format("saas-tenant-%s-%s", tenantId, safeName);
    }

    private void applyModelToStore(String storeId) {
        // TODO: Apply authorization model programmatically
        // For now, log instruction for manual application
        log.info("Apply model to store {} using: fga model write --store-id {} --api-url {} --file openfga/model.fga",
                storeId, storeId, properties.getApiUrl());
    }
}
