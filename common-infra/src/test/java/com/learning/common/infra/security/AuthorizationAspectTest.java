package com.learning.common.infra.security;

import com.learning.common.infra.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationAspectTest {

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthorizationAspect authorizationAspect;

    private final String userId = "user-123";
    private final String tenantId = "tenant-1";
    private final String resource = "entry";
    private final String action = "read";

    @BeforeEach
    void setUp() {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();
    }

    @Test
    void checkPermission_WhenAuthorized_Proceeds() throws Throwable {
        // Setup Context
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        TenantContext.setCurrentTenant(tenantId);

        // Setup Annotation
        RequirePermission annotation = mock(RequirePermission.class);
        lenient().when(annotation.resource()).thenReturn(resource);
        lenient().when(annotation.action()).thenReturn(action);

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
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        TenantContext.setCurrentTenant(tenantId);

        // Setup Annotation
        RequirePermission annotation = mock(RequirePermission.class);
        lenient().when(annotation.resource()).thenReturn(resource);
        lenient().when(annotation.action()).thenReturn(action);

        // Setup Permission Check
        when(permissionEvaluator.hasPermission(userId, tenantId, resource, action)).thenReturn(false);

        // Execute & Verify
        assertThrows(AccessDeniedException.class, () -> authorizationAspect.checkPermission(joinPoint, annotation));

        verify(joinPoint, never()).proceed();
    }

    @Test
    void checkPermission_WhenNoTenantContext_ThrowsException() throws Throwable {
        // Setup Context
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        TenantContext.clear(); // No tenant

        // Setup Annotation
        RequirePermission annotation = mock(RequirePermission.class);

        // Execute & Verify
        assertThrows(AccessDeniedException.class, () -> authorizationAspect.checkPermission(joinPoint, annotation));
    }
}
