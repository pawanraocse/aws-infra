package com.learning.platformservice.organization.controller;

import com.learning.platformservice.organization.dto.CreateOrganizationSettingsRequest;
import com.learning.platformservice.organization.dto.OrganizationProfileDTO;
import com.learning.platformservice.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal controller for organization management.
 * 
 * <p>
 * Used by auth-service during organization signup flow to:
 * <ul>
 * <li>Check if organization settings exist (idempotency check)</li>
 * <li>Initialize organization-specific settings after tenant is
 * provisioned</li>
 * </ul>
 * 
 * <p>
 * Note: This endpoint is internal and should only be called by trusted
 * services.
 * Security is enforced at the gateway level by allowing only internal service
 * calls.
 */
@RestController
@RequestMapping("/internal/organizations")
@RequiredArgsConstructor
@Slf4j
public class InternalOrganizationController {

    private final OrganizationService organizationService;

    /**
     * Check if organization settings exist for a tenant.
     * 
     * <p>
     * Used by CreateOrgSettingsAction.isAlreadyDone() to check
     * idempotency before creating organization settings.
     *
     * @param tenantId Tenant ID to check
     * @return 200 OK if exists, 404 NOT FOUND if not
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<OrganizationProfileDTO> getOrganizationSettings(
            @PathVariable String tenantId) {

        log.debug("Checking if organization settings exist for tenant: {}", tenantId);

        try {
            OrganizationProfileDTO organization = organizationService.getOrganization(tenantId);
            log.debug("Organization settings found for tenant: {}", tenantId);
            return ResponseEntity.ok(organization);
        } catch (IllegalArgumentException e) {
            log.debug("Organization settings not found for tenant: {}", tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create or update initial organization settings.
     * 
     * <p>
     * Called by CreateOrgSettingsAction during organization signup
     * to set up initial organization profile after tenant is provisioned.
     * 
     * <p>
     * This is idempotent - if settings already exist, they will be updated.
     *
     * @param request Organization settings request
     * @return 201 CREATED with the organization profile
     */
    @PostMapping
    public ResponseEntity<OrganizationProfileDTO> createOrganizationSettings(
            @Valid @RequestBody CreateOrganizationSettingsRequest request) {

        log.info("Creating organization settings for tenant: {}", request.getTenantId());

        try {
            OrganizationProfileDTO organization = organizationService.initializeOrganizationSettings(
                    request.getTenantId(),
                    request.getCompanyName(),
                    request.getTier());

            log.info("Organization settings created successfully for tenant: {}", request.getTenantId());
            return ResponseEntity.status(HttpStatus.CREATED).body(organization);

        } catch (IllegalArgumentException e) {
            log.error("Failed to create organization settings for tenant {}: {}",
                    request.getTenantId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
