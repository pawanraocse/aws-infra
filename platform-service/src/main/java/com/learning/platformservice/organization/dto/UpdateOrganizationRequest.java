package com.learning.platformservice.organization.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating organization profile.
 * Contains validation rules for user-editable fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationRequest {

    @Size(max = 255, message = "Company name cannot exceed 255 characters")
    private String companyName;

    @Size(max = 100, message = "Industry cannot exceed 100 characters")
    private String industry;

    @Pattern(regexp = "^(1-10|11-50|51-200|201-500|501-1000|1001\\+)$", message = "Invalid company size. Must be one of: 1-10, 11-50, 51-200, 201-500, 501-1000, 1001+")
    private String companySize;

    @Pattern(regexp = "^(https?://)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([/\\w \\.-]*)*/?$", message = "Invalid website URL format")
    @Size(max = 500, message = "Website URL cannot exceed 500 characters")
    private String website;

    @Pattern(regexp = "^(https?://).+\\.(png|jpg|jpeg|svg)$", message = "Logo URL must be a valid image URL (png, jpg, jpeg, svg)")
    @Size(max = 1000, message = "Logo URL cannot exceed 1000 characters")
    private String logoUrl;
}
