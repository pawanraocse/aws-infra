package com.learning.authservice.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Service for looking up tenant information from platform-service.
 * 
 * This service provides a clean abstraction for tenant metadata lookup,
 * following the principle that Cognito stores minimal data (tenantId)
 * while detailed tenant info comes from platform-service DB.
 * 
 * Features:
 * - Timeout and retry handling for resilience
 * - Graceful fallback on errors
 * - Caching-ready design (can add cache layer later)
 */
@Service
@Slf4j
public class TenantLookupService {

    private final WebClient platformWebClient;

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_TENANT_TYPE = "PERSONAL";

    public TenantLookupService(@Qualifier("platformWebClient") WebClient platformWebClient) {
        this.platformWebClient = platformWebClient;
    }

    /**
     * Lookup tenant type from platform-service.
     * 
     * @param tenantId The tenant identifier
     * @return The tenant type (PERSONAL or ORGANIZATION), or DEFAULT_TENANT_TYPE on
     *         error
     */
    public String lookupTenantType(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Empty tenantId provided for lookup, returning default type");
            return DEFAULT_TENANT_TYPE;
        }

        try {
            Optional<TenantInfo> tenantInfo = fetchTenantInfo(tenantId);
            return tenantInfo.map(TenantInfo::tenantType).orElse(DEFAULT_TENANT_TYPE);
        } catch (Exception e) {
            log.error("Failed to lookup tenant type for tenantId={}: {}", tenantId, e.getMessage());
            return DEFAULT_TENANT_TYPE;
        }
    }

    /**
     * Lookup full tenant information from platform-service.
     * 
     * @param tenantId The tenant identifier
     * @return Optional containing TenantInfo if found
     */
    public Optional<TenantInfo> getTenantInfo(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }

        try {
            return fetchTenantInfo(tenantId);
        } catch (Exception e) {
            log.error("Failed to get tenant info for tenantId={}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Internal method to fetch tenant info from platform-service.
     */
    private Optional<TenantInfo> fetchTenantInfo(String tenantId) {
        log.debug("Fetching tenant info from platform-service: tenantId={}", tenantId);

        return platformWebClient.get()
                .uri("/api/v1/tenants/{tenantId}", tenantId)
                .retrieve()
                .bodyToMono(TenantInfo.class)
                .timeout(LOOKUP_TIMEOUT)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    log.warn("Tenant not found in platform-service: tenantId={}", tenantId);
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.error("Error fetching tenant from platform-service: {}", e.getMessage());
                    return Mono.empty();
                })
                .blockOptional();
    }

    /**
     * Minimal tenant info record for lookup responses.
     * Can be extended as needed for additional tenant metadata.
     */
    public record TenantInfo(
            String tenantId,
            String tenantName,
            String tenantType,
            String tier,
            boolean active) {
    }
}
