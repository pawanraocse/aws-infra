package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.repository.RolePermissionRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    private final String userId = "user-123";
    private final String tenantId = "tenant-1";
    private final String resource = "entry";
    private final String action = "read";

    @Test
    void hasPermission_WhenUserHasRoleWithPermission_ReturnsTrue() {
        UserRole userRole = UserRole.builder().roleId("tenant-user").build();
        when(userRoleRepository.findActiveRolesByUserIdAndTenantId(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(userRole));

        when(rolePermissionRepository.existsByRoleIdAndResourceAndAction("tenant-user", resource, action))
                .thenReturn(true);

        boolean result = permissionService.hasPermission(userId, tenantId, resource, action);

        assertTrue(result);
    }

    @Test
    void hasPermission_WhenUserHasNoRoles_ReturnsFalse() {
        when(userRoleRepository.findActiveRolesByUserIdAndTenantId(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        boolean result = permissionService.hasPermission(userId, tenantId, resource, action);

        assertFalse(result);
    }

    @Test
    void hasPermission_WhenUserHasRoleButNoPermission_ReturnsFalse() {
        UserRole userRole = UserRole.builder().roleId("tenant-guest").build();
        when(userRoleRepository.findActiveRolesByUserIdAndTenantId(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(userRole));

        when(rolePermissionRepository.existsByRoleIdAndResourceAndAction("tenant-guest", resource, action))
                .thenReturn(false);

        boolean result = permissionService.hasPermission(userId, tenantId, resource, action);

        assertFalse(result);
    }

    @Test
    void hasPermission_WhenUserIsSuperAdmin_ReturnsTrue() {
        UserRole userRole = UserRole.builder().roleId("super-admin").build();
        when(userRoleRepository.findActiveRolesByUserIdAndTenantId(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(userRole));

        boolean result = permissionService.hasPermission(userId, tenantId, resource, action);

        assertTrue(result);
    }

    @Test
    void getUserPermissions_ReturnsAllPermissions() {
        UserRole userRole = UserRole.builder().roleId("tenant-user").build();
        when(userRoleRepository.findActiveRolesByUserIdAndTenantId(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(userRole));

        when(rolePermissionRepository.findPermissionIdsByRoleId("tenant-user"))
                .thenReturn(List.of("entry:read", "entry:create"));

        Set<String> permissions = permissionService.getUserPermissions(userId, tenantId);

        assertEquals(2, permissions.size());
        assertTrue(permissions.contains("entry:read"));
        assertTrue(permissions.contains("entry:create"));
    }

    @Test
    void getUserPermissions_WhenSuperAdmin_ReturnsWildcard() {
        UserRole userRole = UserRole.builder().roleId("super-admin").build();
        when(userRoleRepository.findActiveRolesByUserIdAndTenantId(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(userRole));

        Set<String> permissions = permissionService.getUserPermissions(userId, tenantId);

        assertEquals(1, permissions.size());
        assertTrue(permissions.contains("*:*"));
    }
}
