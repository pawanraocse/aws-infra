package com.learning.authservice.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for user statistics within a tenant.
 * Provides admin dashboard metrics for organization overview.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDTO {

    /**
     * Total number of accepted users (includes admins and regular users)
     */
    private Long totalUsers;

    /**
     * Number of pending invitations
     */
    private Long pendingInvitations;

    /**
     * Number of expired invitations
     */
    private Long expiredInvitations;

    /**
     * Number of revoked invitations
     */
    private Long revokedInvitations;

    /**
     * Distribution of users by role (e.g., {"admin": 2, "user": 5})
     */
    private Map<String, Long> roleDistribution;

    /**
     * Number of users with admin role
     */
    private Long adminCount;

    /**
     * Number of users with user role (non-admin)
     */
    private Long regularUserCount;
}
