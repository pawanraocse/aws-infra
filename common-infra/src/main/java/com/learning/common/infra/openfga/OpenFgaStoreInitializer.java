package com.learning.common.infra.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Initializes OpenFGA store and authorization model on application startup.
 * Only active when openfga.enabled=true.
 * 
 * This component:
 * 1. Creates a store if none exists (or uses configured storeId)
 * 2. Writes the authorization model from model.fga
 * 
 * For multi-tenant: Each tenant should get their own store.
 * For development: A single shared store is sufficient.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaStoreInitializer {

    private final OpenFgaProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeStore() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            log.info("Initializing OpenFGA store...");

            var configuration = new ClientConfiguration()
                    .apiUrl(properties.getApiUrl());

            var client = new OpenFgaClient(configuration);

            String storeId = properties.getStoreId();

            // Create store if no storeId configured
            if (storeId == null || storeId.isEmpty()) {
                storeId = createStore(client);
                log.info("Created new OpenFGA store: {}", storeId);
            } else {
                log.info("Using existing OpenFGA store: {}", storeId);
            }

            // Update configuration with store ID
            configuration.storeId(storeId);
            client = new OpenFgaClient(configuration);

            // Apply authorization model
            applyAuthorizationModel(client);

            log.info("✅ OpenFGA initialization complete. Store ID: {}", storeId);

        } catch (Exception e) {
            log.error("❌ OpenFGA initialization failed: {}", e.getMessage(), e);
            // Don't fail startup - OpenFGA is optional
        }
    }

    private String createStore(OpenFgaClient client) throws Exception {
        var request = new CreateStoreRequest()
                .name("saas-template-" + System.currentTimeMillis());

        var response = client.createStore(request).get();
        return response.getId();
    }

    private void applyAuthorizationModel(OpenFgaClient client) throws Exception {
        log.info("Applying authorization model programmatically...");

        // Define 'user' type
        var userType = new dev.openfga.sdk.api.model.TypeDefinition()
                .type("user");

        // Define 'folder' type with simple relations
        var folderType = new dev.openfga.sdk.api.model.TypeDefinition()
                .type("folder")
                .relations(java.util.Map.of(
                        "viewer", new dev.openfga.sdk.api.model.Userset()._this(new Object()),
                        "editor", new dev.openfga.sdk.api.model.Userset()._this(new Object()),
                        "owner", new dev.openfga.sdk.api.model.Userset()._this(new Object())));

        // Define 'document' type
        var documentType = new dev.openfga.sdk.api.model.TypeDefinition()
                .type("document")
                .relations(java.util.Map.of(
                        "viewer", new dev.openfga.sdk.api.model.Userset()._this(new Object()),
                        "editor", new dev.openfga.sdk.api.model.Userset()._this(new Object()),
                        "owner", new dev.openfga.sdk.api.model.Userset()._this(new Object())));

        var request = new dev.openfga.sdk.api.model.WriteAuthorizationModelRequest()
                .schemaVersion("1.1")
                .typeDefinitions(java.util.List.of(userType, folderType, documentType));

        var response = client.writeAuthorizationModel(request).get();
        log.info("✅ Authorization model written. ID: {}", response.getAuthorizationModelId());
    }
}
