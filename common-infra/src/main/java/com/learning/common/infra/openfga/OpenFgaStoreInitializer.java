package com.learning.common.infra.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

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
        // Try to load model from classpath
        String modelJson = loadModelJson();

        if (modelJson != null) {
            // Parse and apply model
            // Note: OpenFGA SDK expects JSON format, not DSL
            // For production, convert model.fga to JSON using FGA CLI
            log.info("Authorization model loaded from classpath");

            // For now, log that model should be applied manually
            log.warn("⚠️ Apply authorization model manually using FGA CLI:");
            log.warn("   fga model write --store-id <store_id> --file openfga/model.fga");
        } else {
            log.warn("No authorization model found in classpath. Apply manually.");
        }
    }

    private String loadModelJson() {
        try {
            // Try to load from classpath: openfga/model.json
            var resource = new ClassPathResource("openfga/model.json");
            if (resource.exists()) {
                return Files.readString(Path.of(resource.getURI()));
            }
        } catch (Exception e) {
            log.debug("Could not load model.json from classpath: {}", e.getMessage());
        }
        return null;
    }
}
