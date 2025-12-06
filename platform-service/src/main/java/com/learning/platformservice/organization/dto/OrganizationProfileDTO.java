package com.learning.platformservice.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for organization profile information.
 * Exposed to tenant admins for managing their organization details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationProfileDTO {
    private String tenantId;
    private String name;
    private String companyName;
    private String industry;
    private String companySize;
    private String website;
    private String logoUrl;
    private String slaTier;
    private String tenantType;
    private Integer maxUsers;
}
