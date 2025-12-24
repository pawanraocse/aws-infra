package com.learning.platformservice.organization.service;

import com.learning.platformservice.organization.dto.OrganizationProfileDTO;
import com.learning.platformservice.organization.dto.UpdateOrganizationRequest;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Service for managing organization profiles.
 * Handles CRUD operations for organization-specific tenant metadata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final TenantRepository tenantRepository;

    /**
     * Get organization profile by tenant ID.
     *
     * @param tenantId Tenant ID
     * @return Organization profile DTO
     */
    @Transactional(readOnly = true)
    public OrganizationProfileDTO getOrganization(String tenantId) {
        log.info("Fetching organization profile for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        return mapToDTO(tenant);
    }

    /**
     * Update organization profile.
     *
     * @param tenantId Tenant ID
     * @param request  Update request with new values
     * @return Updated organization profile
     */
    @Transactional
    public OrganizationProfileDTO updateOrganization(String tenantId, UpdateOrganizationRequest request) {
        log.info("Updating organization profile for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Update only provided fields (partial update)
        if (request.getCompanyName() != null) {
            tenant.setCompanyName(request.getCompanyName());
        }
        if (request.getIndustry() != null) {
            tenant.setIndustry(request.getIndustry());
        }
        if (request.getCompanySize() != null) {
            tenant.setCompanySize(request.getCompanySize());
        }
        if (request.getWebsite() != null) {
            tenant.setWebsite(request.getWebsite());
        }
        if (request.getLogoUrl() != null) {
            tenant.setLogoUrl(request.getLogoUrl());
        }

        tenant.setUpdatedAt(OffsetDateTime.now());
        Tenant savedTenant = tenantRepository.save(tenant);

        log.info("Organization profile updated successfully for tenant: {}", tenantId);
        return mapToDTO(savedTenant);
    }

    /**
     * Map Tenant entity to OrganizationProfileDTO.
     */
    private OrganizationProfileDTO mapToDTO(Tenant tenant) {
        return OrganizationProfileDTO.builder()
                .tenantId(tenant.getId())
                .name(tenant.getName())
                .companyName(tenant.getCompanyName())
                .industry(tenant.getIndustry())
                .companySize(tenant.getCompanySize())
                .website(tenant.getWebsite())
                .logoUrl(tenant.getLogoUrl())
                .slaTier(tenant.getSlaTier())
                .tenantType(tenant.getTenantType() != null ? tenant.getTenantType().name() : null)
                .maxUsers(tenant.getMaxUsers())
                .ownerEmail(tenant.getOwnerEmail())
                .subscriptionStatus(
                        tenant.getSubscriptionStatus() != null ? tenant.getSubscriptionStatus().name() : null)
                .trialEndsAt(tenant.getTrialEndsAt() != null ? tenant.getTrialEndsAt().toString() : null)
                .build();
    }
}
