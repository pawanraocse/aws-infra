package com.learning.common.infra.security;

import com.learning.common.infra.exception.PermissionDeniedException;
import com.learning.common.infra.openfga.OpenFgaPermissionEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationAspectTest {

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @Mock
    private RoleLookupService roleLookupService;

    @Mock
    private PermissionAuditLogger auditLogger;

    @Mock
    private OpenFgaPermissionEvaluator fgaEvaluator;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    private AuthorizationAspect aspectWithFga;
    private AuthorizationAspect aspectWithoutFga;

    private final String userId = "user-123";
    private final String tenantId = "tenant-1";
    private final String resource = "entry";
    private final String action = "read";

    @BeforeEach
    void setUp() {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attributes);

        // Aspect WITH OpenFGA enabled
        aspectWithFga = new AuthorizationAspect(permissionEvaluator, roleLookupService, auditLogger);
        // Use reflection to inject fgaEvaluator
        try {
            var field = AuthorizationAspect.class.getDeclaredField("fgaEvaluator");
            field.setAccessible(true);
            field.set(aspectWithFga, fgaEvaluator);
        } catch (Exception e) {
            fail("Failed to inject fgaEvaluator: " + e.getMessage());
        }

        // Aspect WITHOUT OpenFGA (fgaEvaluator remains null)
        aspectWithoutFga = new AuthorizationAspect(permissionEvaluator, roleLookupService, auditLogger);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("RBAC Tests (without OpenFGA)")
    class RbacTests {

        @Test
        void checkPermission_WhenAuthorized_Proceeds() throws Throwable {
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(false);

            RequirePermission annotation = mockAnnotation(resource, action, "");
            when(permissionEvaluator.hasPermission(userId, resource, action)).thenReturn(true);

            aspectWithoutFga.checkPermission(joinPoint, annotation);

            verify(joinPoint).proceed();
            verify(auditLogger).logOrgLevelCheck(userId, tenantId, resource, action,
                    PermissionAuditLogger.Decision.ALLOWED_RBAC);
        }

        @Test
        void checkPermission_WhenUnauthorized_ThrowsAndLogsDeNied() throws Throwable {
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(false);

            RequirePermission annotation = mockAnnotation(resource, action, "");
            when(permissionEvaluator.hasPermission(userId, resource, action)).thenReturn(false);

            assertThrows(PermissionDeniedException.class,
                    () -> aspectWithoutFga.checkPermission(joinPoint, annotation));

            verify(joinPoint, never()).proceed();
            verify(auditLogger).logOrgLevelCheck(userId, tenantId, resource, action,
                    PermissionAuditLogger.Decision.DENIED);
        }

        @Test
        void checkPermission_WhenSuperAdmin_ProceedsAndLogsAdmin() throws Throwable {
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(true);

            RequirePermission annotation = mockAnnotation(resource, action, "");

            aspectWithoutFga.checkPermission(joinPoint, annotation);

            verify(joinPoint).proceed();
            verify(permissionEvaluator, never()).hasPermission(any(), any(), any());
            verify(auditLogger).logOrgLevelCheck(userId, tenantId, resource, action,
                    PermissionAuditLogger.Decision.ALLOWED_SUPER_ADMIN);
        }

        @Test
        void checkPermission_WhenNoUser_ThrowsException() throws Throwable {
            when(request.getHeader("X-User-Id")).thenReturn(null);

            RequirePermission annotation = mockAnnotation(resource, action, "");

            assertThrows(PermissionDeniedException.class,
                    () -> aspectWithoutFga.checkPermission(joinPoint, annotation));
        }
    }

    @Nested
    @DisplayName("OpenFGA Resource-Level Tests")
    class OpenFgaTests {

        @Test
        void checkPermission_WhenFgaAllowed_ProceedsAndLogsFga() throws Throwable {
            String resourceId = "doc-456";
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(false);

            RequirePermission annotation = mockAnnotation("document", "edit", "id");
            mockJoinPointWithResourceId(resourceId);

            when(fgaEvaluator.hasResourcePermission(userId, "document", resourceId, "edit"))
                    .thenReturn(true);

            aspectWithFga.checkPermission(joinPoint, annotation);

            verify(joinPoint).proceed();
            verify(auditLogger).logResourceLevelCheck(userId, tenantId, "document", resourceId, "edit",
                    PermissionAuditLogger.Decision.ALLOWED_FGA);
            // RBAC should NOT be called when FGA allows
            verify(permissionEvaluator, never()).hasPermission(any(), any(), any());
        }

        @Test
        void checkPermission_WhenFgaDenied_FallsBackToRbac() throws Throwable {
            String resourceId = "doc-456";
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(false);

            RequirePermission annotation = mockAnnotation("document", "edit", "id");
            mockJoinPointWithResourceId(resourceId);

            // FGA denies
            when(fgaEvaluator.hasResourcePermission(userId, "document", resourceId, "edit"))
                    .thenReturn(false);
            // RBAC allows
            when(permissionEvaluator.hasPermission(userId, "document", "edit")).thenReturn(true);

            aspectWithFga.checkPermission(joinPoint, annotation);

            verify(joinPoint).proceed();
            verify(auditLogger).logOrgLevelCheck(userId, tenantId, "document", "edit",
                    PermissionAuditLogger.Decision.ALLOWED_RBAC);
        }

        @Test
        void checkPermission_WhenBothDeny_ThrowsAndLogsResourceDenied() throws Throwable {
            String resourceId = "doc-456";
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(false);

            RequirePermission annotation = mockAnnotation("document", "edit", "id");
            mockJoinPointWithResourceId(resourceId);

            // Both deny
            when(fgaEvaluator.hasResourcePermission(userId, "document", resourceId, "edit"))
                    .thenReturn(false);
            when(permissionEvaluator.hasPermission(userId, "document", "edit")).thenReturn(false);

            assertThrows(PermissionDeniedException.class,
                    () -> aspectWithFga.checkPermission(joinPoint, annotation));

            verify(auditLogger).logResourceLevelCheck(userId, tenantId, "document", resourceId, "edit",
                    PermissionAuditLogger.Decision.DENIED);
        }

        @Test
        void checkPermission_WhenFgaDisabled_SkipsOpenFgaCheck() throws Throwable {
            when(request.getHeader("X-User-Id")).thenReturn(userId);
            when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId);
            when(roleLookupService.isSuperAdmin(userId, tenantId)).thenReturn(false);

            // Has resourceIdParam but FGA is disabled (using aspectWithoutFga)
            RequirePermission annotation = mockAnnotation("document", "edit", "id");
            when(permissionEvaluator.hasPermission(userId, "document", "edit")).thenReturn(true);

            aspectWithoutFga.checkPermission(joinPoint, annotation);

            verify(joinPoint).proceed();
            // No FGA interaction
            verifyNoInteractions(fgaEvaluator);
        }
    }

    // Helper methods
    private RequirePermission mockAnnotation(String resource, String action, String resourceIdParam) {
        RequirePermission annotation = mock(RequirePermission.class);
        lenient().when(annotation.resource()).thenReturn(resource);
        lenient().when(annotation.action()).thenReturn(action);
        lenient().when(annotation.resourceIdParam()).thenReturn(resourceIdParam);
        return annotation;
    }

    private void mockJoinPointWithResourceId(String resourceId) throws Exception {
        MethodSignature signature = mock(MethodSignature.class);
        Method method = TestController.class.getMethod("testMethod", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[] { resourceId });
    }

    // Dummy controller for method signature testing
    static class TestController {
        public void testMethod(String id) {
        }
    }
}
