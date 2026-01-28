package com.learning.authservice.authorization.service;

import com.learning.common.infra.security.PermissionEvaluator;
import com.learning.common.infra.security.RoleLookupService;
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Local implementation of PermissionEvaluator for Auth Service.
 * Uses PermissionService directly since it's in the same application.
 * Tenant context is implicit via TenantDataSourceRouter.
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class AuthServicePermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;
    private final RoleLookupService roleLookupService;

    @Override
    public boolean hasPermission(String userId, String resource, String action) {
        String tenantId = TenantContext.getCurrentTenant();

        // 1. Admin/Super-admin bypass - matches RemotePermissionEvaluator logic
        if (roleLookupService.hasAdminAccess(userId, tenantId)) {
            log.debug("Admin bypass allowed: user={} resource={} action={}", userId, resource, action);
            return true;
        }

        // 2. Fallback to fine-grained permission check in DB
        return permissionService.hasPermission(userId, resource, action);
    }
}
