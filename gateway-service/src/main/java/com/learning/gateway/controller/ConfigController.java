package com.learning.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public configuration endpoint for frontend SPA.
 * Exposes non-sensitive Cognito configuration needed by the Angular app.
 * 
 * This endpoint is intentionally PUBLIC (no auth required) because:
 * 1. Frontend needs this config BEFORE it can authenticate
 * 2. userPoolId, clientId, and region are public identifiers (not secrets)
 * 3. AWS Cognito expects SPA client IDs to be public
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${cognito.spa.user-pool-id:}")
    private String userPoolId;

    @Value("${cognito.spa.client-id:}")
    private String clientId;

    @Value("${cognito.spa.region:us-east-1}")
    private String region;

    /**
     * Returns Cognito configuration for the frontend SPA.
     * This endpoint is public - no authentication required.
     */
    @GetMapping("/cognito")
    public Mono<CognitoConfig> getCognitoConfig() {
        return Mono.just(new CognitoConfig(userPoolId, clientId, region));
    }

    /**
     * DTO for Cognito configuration response.
     */
    public record CognitoConfig(
            String userPoolId,
            String clientId,
            String region) {
    }
}
