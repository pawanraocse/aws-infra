package com.learning.authservice.permission.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for granting access to a resource.
 */
public record SharePermissionRequest(
        @NotBlank(message = "Target user ID is required") String targetUserId,

        @NotBlank(message = "Resource type is required") String resourceType,

        @NotBlank(message = "Resource ID is required") String resourceId,

        @NotBlank(message = "Relation is required (e.g., viewer, editor, owner)") String relation) {
}
