package com.learning.authservice.stats.controller;

import com.learning.authservice.stats.dto.UserStatsDTO;
import com.learning.authservice.stats.service.UserStatsService;
import com.learning.common.infra.security.RequirePermission;
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user statistics.
 * Provides aggregated metrics for admin dashboard.
 */
@RestController
@RequestMapping("/api/v1/stats/users")
@RequiredArgsConstructor
@Slf4j
public class UserStatsController {

    private final UserStatsService userStatsService;

    /**
     * Get user statistics for current tenant.
     * 
     * @return User statistics DTO with counts and role distribution
     */
    @GetMapping
    @RequirePermission(resource = "stats", action = "read")
    public ResponseEntity<UserStatsDTO> getUserStats() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Getting user statistics for tenant: {}", tenantId);

        UserStatsDTO stats = userStatsService.getUserStats(tenantId);
        return ResponseEntity.ok(stats);
    }
}
