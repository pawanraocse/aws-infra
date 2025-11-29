package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.service.PermissionService;
import com.learning.common.infra.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Local implementation of PermissionEvaluator for Auth Service.
 * Uses PermissionService directly since it's in the same application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServicePermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;

    @Override
    public boolean hasPermission(String userId, String tenantId, String resource, String action) {
        return permissionService.hasPermission(userId, tenantId, resource, action);
    }
}
