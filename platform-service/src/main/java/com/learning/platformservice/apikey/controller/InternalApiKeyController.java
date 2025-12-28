package com.learning.platformservice.apikey.controller;

import com.learning.platformservice.apikey.dto.ValidatedApiKey;
import com.learning.platformservice.apikey.service.ApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal controller for gateway to validate API keys.
 * Not exposed to external traffic (service-to-service only).
 */
@RestController
@RequestMapping("/internal/api-keys")
public class InternalApiKeyController {

    private final ApiKeyService apiKeyService;

    public InternalApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * Validate an API key (called by gateway).
     * 
     * @param key The raw API key to validate
     * @return Validation result with tenant/user info or error code
     */
    @GetMapping("/validate")
    public ResponseEntity<ValidatedApiKey> validateApiKey(@RequestParam("key") String key) {
        ValidatedApiKey result = apiKeyService.validateApiKey(key);
        return ResponseEntity.ok(result);
    }
}
