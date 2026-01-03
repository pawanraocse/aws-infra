package com.learning.authservice.signup;

import com.learning.authservice.exception.AuthSignupException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Strategy for generating tenant IDs based on signup type.
 * Uses pattern matching to handle different signup types.
 * 
 * <p>
 * <b>Collision Prevention:</b> For organization signups, checks if the
 * slugified company name already exists. If it exists with the same owner,
 * returns the existing ID (idempotency). If different owner, throws error.
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
     * @throws AuthSignupException if organization name already exists with
     *                             different owner
     */
    public String generate(SignupRequest request) {
        return switch (request) {
            case PersonalSignupData p -> generatePersonalTenantId(p.email());
            case OrganizationSignupData o -> generateOrganizationTenantId(o.companyName(), o.email());
        };
    }

    /**
     * Generate a personal tenant ID from email.
     * Format: user-{sanitized-username}-{timestamp}
     */
    public String generatePersonalTenantId(String email) {
        String username = email.split("@")[0];
        String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return "user-" + sanitized + "-" + timestamp;
    }

    /**
     * Generate an organization tenant ID from company name.
     * If tenant exists with same owner, returns existing ID (idempotency).
     * If tenant exists with different owner, throws AuthSignupException.
     */
    public String generateOrganizationTenantId(String companyName, String ownerEmail) {
        String slug = slugify(companyName);

        // Check if tenant already exists
        TenantExistsResult result = checkTenantExists(slug);

        if (result.exists()) {
            if (ownerEmail != null && ownerEmail.equalsIgnoreCase(result.ownerEmail())) {
                // Same owner retrying - return existing ID for idempotency
                log.info("Tenant {} already exists with same owner {}, returning existing ID (idempotency)",
                        slug, ownerEmail);
                return slug;
            } else {
                // Different owner - block
                log.info("Organization name already exists: {} (slug: {}) with different owner", companyName, slug);
                throw new AuthSignupException(
                        "COMPANY_NAME_EXISTS",
                        "An organization with this name already exists. Please choose a different name.");
            }
        }

        log.debug("Tenant ID available: {}", slug);
        return slug;
    }

    /**
     * Backward compatibility - generate without owner check (new signup).
     */
    public String generateOrganizationTenantId(String companyName) {
        return generateOrganizationTenantId(companyName, null);
    }

    /**
     * Check if a tenant ID already exists and get owner email.
     */
    private TenantExistsResult checkTenantExists(String tenantId) {
        try {
            // Try to get tenant details
            var response = platformWebClient.get()
                    .uri("/internal/tenants/{tenantId}", tenantId)
                    .retrieve()
                    .bodyToMono(TenantResponse.class)
                    .block();

            if (response != null) {
                return new TenantExistsResult(true, response.ownerEmail());
            }
            return new TenantExistsResult(false, null);
        } catch (WebClientResponseException.NotFound e) {
            return new TenantExistsResult(false, null);
        } catch (Exception e) {
            log.warn("Failed to check tenant existence: {} - assuming available", tenantId, e);
            // Assume available to allow signup (better UX than blocking)
            return new TenantExistsResult(false, null);
        }
    }

    private String slugify(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private record TenantExistsResult(boolean exists, String ownerEmail) {
    }

    private record TenantResponse(String id, String ownerEmail) {
    }
}
