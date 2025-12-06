package com.learning.platformservice.organization.controller;

import com.learning.common.infra.security.RequirePermission;
import com.learning.common.infra.tenant.TenantContext;
import com.learning.platformservice.organization.dto.OrganizationProfileDTO;
import com.learning.platformservice.organization.dto.UpdateOrganizationRequest;
import com.learning.platformservice.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for organization profile management.
 * Provides endpoints for tenant admins to view and update their organization
 * details.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * Get organization profile for current tenant.
     *
     * @return Organization profile DTO
     */
    @GetMapping
    @RequirePermission(resource = "organization", action = "read")
    public ResponseEntity<OrganizationProfileDTO> getOrganization() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting organization profile for tenant: {}", tenantId);

        OrganizationProfileDTO organization = organizationService.getOrganization(tenantId);
        return ResponseEntity.ok(organization);
    }

    /**
     * Update organization profile for current tenant.
     *
     * @param request Update request with new values
     * @return Updated organization profile
     */
    @PutMapping
    @RequirePermission(resource = "organization", action = "manage")
    public ResponseEntity<OrganizationProfileDTO> updateOrganization(
            @Valid @RequestBody UpdateOrganizationRequest request) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("Updating organization profile for tenant: {}", tenantId);

        OrganizationProfileDTO updated = organizationService.updateOrganization(tenantId, request);
        return ResponseEntity.ok(updated);
    }
}
