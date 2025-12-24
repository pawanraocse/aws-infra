package com.learning.authservice.signup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Strategy for generating tenant IDs based on signup type.
 * Uses pattern matching to handle different signup types.
 * 
 * <p>
 * <b>Collision Prevention:</b> For organization signups, checks if the
 * slugified company name already exists and appends a numeric suffix if needed
 * (e.g., acme-inc, acme-inc-2, acme-inc-3).
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantIdGenerator {

    private final WebClient platformWebClient;

    @Value("${tenant.id.max-collision-attempts:10}")
    private int maxCollisionAttempts;

    /**
     * Generate a tenant ID based on signup type.
     * Personal: user-{sanitized-username}-{timestamp}
     * Organization: slugified company name (with unique suffix if collision)
     */
    public String generate(SignupRequest request) {
        return switch (request) {
            case PersonalSignupData p -> generatePersonalTenantId(p.email());
            case OrganizationSignupData o -> generateUniqueOrganizationId(o.companyName());
        };
    }

    private String generatePersonalTenantId(String email) {
        String username = email.split("@")[0];
        String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return "user-" + sanitized + "-" + timestamp;
    }

    /**
     * Generate a unique organization tenant ID.
     * First tries the plain slugified name, then appends -2, -3, etc. if collision.
     */
    private String generateUniqueOrganizationId(String companyName) {
        String baseSlug = slugify(companyName);

        // First, try the base slug
        if (!tenantExists(baseSlug)) {
            log.debug("Tenant ID available: {}", baseSlug);
            return baseSlug;
        }

        // Base slug exists, try adding numeric suffix
        for (int i = 2; i <= maxCollisionAttempts + 1; i++) {
            String candidateId = baseSlug + "-" + i;
            if (!tenantExists(candidateId)) {
                log.info("Tenant ID collision resolved: {} -> {}", baseSlug, candidateId);
                return candidateId;
            }
        }

        // Fallback: append timestamp (should rarely happen)
        String fallbackId = baseSlug + "-" + System.currentTimeMillis();
        log.warn("Tenant ID collision fallback used: {} (max attempts exceeded)", fallbackId);
        return fallbackId;
    }

    /**
     * Check if a tenant ID already exists by calling platform-service.
     */
    private boolean tenantExists(String tenantId) {
        try {
            Boolean exists = platformWebClient.get()
                    .uri("/internal/tenants/{tenantId}/exists", tenantId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Failed to check tenant existence: {} - assuming collision", tenantId, e);
            // Fail safe: assume it exists to avoid collision
            return true;
        }
    }

    private String slugify(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
