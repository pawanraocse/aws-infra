package com.learning.authservice.permission.dto;

import java.util.List;

/**
 * Response DTO for listing access grants on a resource.
 */
public record ResourceAccessResponse(
        String resourceType,
        String resourceId,
        List<AccessGrant> grants) {
    public record AccessGrant(
            String userId,
            String relation) {
    }
}
