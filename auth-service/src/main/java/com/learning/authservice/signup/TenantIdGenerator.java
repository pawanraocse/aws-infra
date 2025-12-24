package com.learning.authservice.signup;

import com.learning.authservice.exception.AuthSignupException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Strategy for generating tenant IDs based on signup type.
 * Uses pattern matching to handle different signup types.
 * 
 * <p>
 * <b>Collision Prevention:</b> For organization signups, checks if the
 * slugified company name already exists and throws an error if so.
 * Users must choose a unique company name.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantIdGenerator {

    private final WebClient platformWebClient;

    /**
     * Generate a tenant ID based on signup type.
     * Personal: user-{sanitized-username}-{timestamp}
     * Organization: slugified company name (must be unique)
     * 
     * @throws AuthSignupException if organization name already exists
     */
    public String generate(SignupRequest request) {
        return switch (request) {
            case PersonalSignupData p -> generatePersonalTenantId(p.email());
            case OrganizationSignupData o -> generateOrganizationId(o.companyName());
        };
    }

    private String generatePersonalTenantId(String email) {
        String username = email.split("@")[0];
        String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return "user-" + sanitized + "-" + timestamp;
    }

    /**
     * Generate organization tenant ID from company name.
     * Throws AuthSignupException if the name already exists.
     */
    private String generateOrganizationId(String companyName) {
        String slug = slugify(companyName);

        if (tenantExists(slug)) {
            log.info("Organization name already exists: {} (slug: {})", companyName, slug);
            throw new AuthSignupException(
                    "COMPANY_NAME_EXISTS",
                    "An organization with this name already exists. Please choose a different name.");
        }

        log.debug("Tenant ID available: {}", slug);
        return slug;
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
            log.warn("Failed to check tenant existence: {} - assuming available", tenantId, e);
            // Changed: assume available to allow signup (better UX than blocking)
            return false;
        }
    }

    private String slugify(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
