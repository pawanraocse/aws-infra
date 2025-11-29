package com.learning.authservice.authorization.service;

import com.learning.authservice.authorization.domain.Role;
import com.learning.authservice.authorization.domain.UserRole;
import com.learning.authservice.authorization.repository.RoleRepository;
import com.learning.authservice.authorization.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserRoleService userRoleService;

    private final String userId = "user-123";
    private final String tenantId = "tenant-1";
    private final String roleId = "tenant-admin";
    private final String assignedBy = "admin-user";

    @Test
    void assignRole_WhenRoleExistsAndNotAssigned_SavesAssignment() {
        Role role = Role.builder().id(roleId).scope(Role.RoleScope.TENANT).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.existsByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId)).thenReturn(false);

        userRoleService.assignRole(userId, tenantId, roleId, assignedBy);

        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void assignRole_WhenRoleDoesNotExist_ThrowsException() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.assignRole(userId, tenantId, roleId, assignedBy));

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assignRole_WhenAssignmentExists_DoesNothing() {
        Role role = Role.builder().id(roleId).scope(Role.RoleScope.TENANT).build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.existsByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId)).thenReturn(true);

        userRoleService.assignRole(userId, tenantId, roleId, assignedBy);

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void revokeRole_WhenAssignmentExists_DeletesAssignment() {
        when(userRoleRepository.existsByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId)).thenReturn(true);

        userRoleService.revokeRole(userId, tenantId, roleId);

        verify(userRoleRepository).deleteByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId);
    }

    @Test
    void revokeRole_WhenAssignmentDoesNotExist_DoesNothing() {
        when(userRoleRepository.existsByUserIdAndTenantIdAndRoleId(userId, tenantId, roleId)).thenReturn(false);

        userRoleService.revokeRole(userId, tenantId, roleId);

        verify(userRoleRepository, never()).deleteByUserIdAndTenantIdAndRoleId(any(), any(), any());
    }
}
