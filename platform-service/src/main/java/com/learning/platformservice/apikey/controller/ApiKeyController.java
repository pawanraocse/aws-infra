package com.learning.platformservice.apikey.controller;

import com.learning.platformservice.apikey.dto.ApiKeyResponse;
import com.learning.platformservice.apikey.dto.CreateApiKeyRequest;
import com.learning.platformservice.apikey.dto.CreateApiKeyResponse;
import com.learning.platformservice.apikey.service.ApiKeyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for API key management.
 * Requires JWT authentication for management endpoints.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * Create a new API key.
     * The raw key is returned ONLY in this response.
     */
    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createApiKey(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody CreateApiKeyRequest request) {

        log.info("Creating API key for tenant: {}", tenantId);

        // TODO: Add tier-based limit check here
        // long activeKeys = apiKeyService.countActiveKeys(tenantId);
        // if (activeKeys >= tenantKeyLimit) { ... }

        CreateApiKeyResponse response = apiKeyService.createApiKey(
                tenantId, userId, userEmail != null ? userEmail : "unknown", request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all API keys for the current tenant.
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(
            @RequestHeader("X-Tenant-Id") String tenantId) {

        List<ApiKeyResponse> keys = apiKeyService.listApiKeys(tenantId);
        return ResponseEntity.ok(keys);
    }

    /**
     * Revoke an API key.
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Map<String, String>> revokeApiKey(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable UUID keyId) {

        boolean revoked = apiKeyService.revokeApiKey(tenantId, keyId);

        if (revoked) {
            return ResponseEntity.ok(Map.of("message", "API key revoked successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
