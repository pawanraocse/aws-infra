package com.learning.common.infra.security;

import com.learning.common.infra.tenant.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationAspectTest {

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthorizationAspect authorizationAspect;

    private final String userId = "user-123";
    private final String tenantId = "tenant-1";
    private final String resource = "entry";
    private final String action = "read";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void checkPermission_WhenAuthorized_Proceeds() throws Throwable {
        // Setup Context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId);
        TenantContext.setCurrentTenant(tenantId);

        // Setup Annotation
        RequirePermission annotation = mock(RequirePermission.class);
        lenient().when(annotation.resource()).thenReturn(resource);
        lenient().when(annotation.action()).thenReturn(action);

        // Setup JoinPoint
        // Setup JoinPoint
        // No need to mock signature as it's not used in the aspect

        // Setup Permission Check
        when(permissionEvaluator.hasPermission(userId, tenantId, resource, action)).thenReturn(true);

        // Execute
        authorizationAspect.checkPermission(joinPoint, annotation);

        // Verify
        verify(joinPoint).proceed();
    }

    @Test
    void checkPermission_WhenUnauthorized_ThrowsException() throws Throwable {
        // Setup Context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId);
        TenantContext.setCurrentTenant(tenantId);

        // Setup Annotation
        RequirePermission annotation = mock(RequirePermission.class);
        lenient().when(annotation.resource()).thenReturn(resource);
        lenient().when(annotation.action()).thenReturn(action);

        // Setup JoinPoint
        // Setup JoinPoint
        // No need to mock signature as it's not used in the aspect

        // Setup Permission Check
        when(permissionEvaluator.hasPermission(userId, tenantId, resource, action)).thenReturn(false);

        // Execute & Verify
        assertThrows(AccessDeniedException.class, () -> authorizationAspect.checkPermission(joinPoint, annotation));

        verify(joinPoint, never()).proceed();
    }

    @Test
    void checkPermission_WhenNoTenantContext_ThrowsException() throws Throwable {
        // Setup Context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId);
        TenantContext.clear(); // No tenant

        // Setup Annotation
        RequirePermission annotation = mock(RequirePermission.class);

        // Execute & Verify
        assertThrows(AccessDeniedException.class, () -> authorizationAspect.checkPermission(joinPoint, annotation));
    }

    public void dummyMethod() {
    }
}
