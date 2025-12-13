package com.learning.authservice.controller;

import com.learning.authservice.dto.TenantLookupResponse;
import com.learning.authservice.dto.TenantLookupResult;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/**
 * Controller for tenant lookup operations during login flow.
 * 
 * <p>
 * These endpoints support the multi-tenant login flow where users
 * can select which tenant to access after entering their email.
 * </p>
 * 
 * <h3>Security Note:</h3>
 * <p>
 * The lookup endpoint is intentionally public to allow tenant discovery
 * before authentication. However, it is designed to prevent email enumeration
 * by returning empty results for unknown emails without error.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TenantLookupController {

    private final WebClient platformWebClient;

    /**
     * Lookup all tenants associated with an email address.
     * 
     * <p>
     * PUBLIC endpoint - no authentication required.
     * </p>
     * 
     * <p>
     * Returns tenant information needed for the login selector:
     * <ul>
     * <li>Tenant name and type (Personal/Organization)</li>
     * <li>Company name and logo</li>
     * <li>SSO configuration</li>
     * <li>User's role hint</li>
     * </ul>
     * </p>
     *
     * @param email User's email address
     * @return TenantLookupResult with list of tenants and flow control info
     */
    @GetMapping("/lookup")
    public ResponseEntity<TenantLookupResult> lookupTenants(
            @RequestParam @NotBlank @Email(message = "Invalid email format") String email) {

        log.info("operation=lookupTenants email={}", maskEmail(email));

        try {
            List<TenantLookupResponse> tenants = platformWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/memberships/by-email")
                            .queryParam("email", email)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TenantLookupResponse>>() {
                    })
                    .block();

            if (tenants == null) {
                tenants = Collections.emptyList();
            }

            // Determine flow behavior based on tenant count
            boolean requiresSelection = tenants.size() > 1;
            String defaultTenantId = tenants.stream()
                    .filter(TenantLookupResponse::isDefault)
                    .map(TenantLookupResponse::getTenantId)
                    .findFirst()
                    .orElse(tenants.isEmpty() ? null : tenants.get(0).getTenantId());

            log.info("operation=lookupTenants email={} tenantsFound={} requiresSelection={}",
                    maskEmail(email), tenants.size(), requiresSelection);

            return ResponseEntity.ok(TenantLookupResult.builder()
                    .email(email)
                    .tenants(tenants)
                    .requiresSelection(requiresSelection)
                    .defaultTenantId(defaultTenantId)
                    .build());

        } catch (Exception e) {
            log.error("operation=lookupTenants status=error email={} error={}",
                    maskEmail(email), e.getMessage());
            // Return empty result for security (don't leak internal errors)
            return ResponseEntity.ok(TenantLookupResult.builder()
                    .email(email)
                    .tenants(Collections.emptyList())
                    .requiresSelection(false)
                    .defaultTenantId(null)
                    .build());
        }
    }

    /**
     * Update last accessed timestamp after successful login.
     * Called by frontend after authentication completes.
     *
     * @param email    User's email
     * @param tenantId Tenant that was accessed
     */
    @PatchMapping("/last-accessed")
    public ResponseEntity<Void> updateLastAccessed(
            @RequestParam @NotBlank @Email String email,
            @RequestParam @NotBlank String tenantId) {

        log.debug("operation=updateLastAccessed tenantId={}", tenantId);

        try {
            platformWebClient
                    .patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/memberships/last-accessed")
                            .queryParam("email", email)
                            .queryParam("tenantId", tenantId)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            // Log but don't fail - this is non-critical
            log.warn("operation=updateLastAccessed status=error tenantId={} error={}",
                    tenantId, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Mask email for logging (GDPR compliance).
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
