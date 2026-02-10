package com.learning.common.infra.security;

import com.learning.common.grpc.auth.PermissionCheckRequest;
import com.learning.common.grpc.auth.PermissionCheckResponse;
import com.learning.common.grpc.auth.PermissionServiceGrpc;
import com.learning.common.infra.cache.CacheNames;
import com.learning.common.infra.tenant.TenantContext;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Permission evaluator that checks role-based access.
 *
 * <p>Supports two transport modes for the remote auth-service call:</p>
 * <ul>
 *   <li><strong>gRPC (default)</strong> — HTTP/2 + Protobuf, ~4x faster than REST</li>
 *   <li><strong>REST (fallback)</strong> — WebClient, used when gRPC is disabled or unavailable</li>
 * </ul>
 *
 * <p>The transport mode is controlled by {@code app.grpc.enabled} property.
 * If gRPC call fails at runtime, automatically falls back to REST for that request.</p>
 *
 * <p>With the simplified permission model:</p>
 * <ul>
 *   <li>Admin/Super-admin roles get full access (bypass)</li>
 *   <li>Editor role can read/write resources</li>
 *   <li>Viewer role can only read</li>
 *   <li>Guest role has minimal access</li>
 * </ul>
 */
@Slf4j
public class RemotePermissionEvaluator implements PermissionEvaluator {

    private final WebClient authWebClient;
    private final RoleLookupService roleLookupService;

    @GrpcClient("auth-grpc")
    private PermissionServiceGrpc.PermissionServiceBlockingStub permissionStub;

    @Value("${app.grpc.enabled:true}")
    private boolean grpcEnabled;

    // Roles that have full access
    private static final Set<String> ADMIN_ROLES = Set.of("admin", "super-admin");

    // Roles that can read anything
    private static final Set<String> READ_ROLES = Set.of("admin", "super-admin", "editor", "viewer");

    // Roles that can write (create/update/delete)
    private static final Set<String> WRITE_ROLES = Set.of("admin", "super-admin", "editor");

    private static final long GRPC_DEADLINE_SECONDS = 2;

    public RemotePermissionEvaluator(WebClient authWebClient, RoleLookupService roleLookupService) {
        this.authWebClient = authWebClient;
        this.roleLookupService = roleLookupService;
    }

    @Override
    @Cacheable(value = CacheNames.PERMISSIONS, key = "#userId + ':' + #resource + ':' + #action")
    public boolean hasPermission(String userId, String resource, String action) {
        String tenantId = TenantContext.getCurrentTenant();

        // Look up role from database via RoleLookupService
        String role = roleLookupService.getUserRole(userId, tenantId).orElse(null);
        log.debug("Checking permission: user={}, resource={}, action={}, role={}, tenant={}",
                userId, resource, action, role, tenantId);

        // Admin/Super-admin bypass - full access to everything
        if (role != null && ADMIN_ROLES.contains(role)) {
            log.debug("Admin bypass: user={}, role={}", userId, role);
            return true;
        }

        // Role-based access check for simplified model
        if (role != null) {
            boolean allowed = checkRoleBasedAccess(role, action);
            if (allowed) {
                log.debug("Role-based access granted: user={}, role={}, action={}", userId, role, action);
                return true;
            }
        }

        // Fallback to remote auth-service check for complex cases
        return checkRemotePermission(userId, resource, action, tenantId);
    }

    /**
     * Check access based on role and action type.
     * Simplified model: admin=all, editor=read+write, viewer=read, guest=minimal
     */
    private boolean checkRoleBasedAccess(String role, String action) {
        // Read actions
        if ("read".equals(action) || "view".equals(action) || "list".equals(action)) {
            return READ_ROLES.contains(role);
        }
        // Write actions
        if ("create".equals(action) || "update".equals(action) || "delete".equals(action) ||
                "write".equals(action) || "edit".equals(action)) {
            return WRITE_ROLES.contains(role);
        }
        // Share action - editor and above
        if ("share".equals(action)) {
            return WRITE_ROLES.contains(role);
        }
        return false;
    }

    /**
     * Fallback to remote auth-service permission check.
     * Attempts gRPC first (if enabled), falls back to REST on failure.
     */
    private boolean checkRemotePermission(String userId, String resource, String action, String tenantId) {
        if (grpcEnabled && permissionStub != null) {
            try {
                return checkViaGrpc(userId, resource, action, tenantId);
            } catch (StatusRuntimeException e) {
                log.warn("gRPC permission check failed (status={}), falling back to REST: {}",
                        e.getStatus().getCode(), e.getMessage());
                return checkViaRest(userId, resource, action, tenantId);
            }
        }
        return checkViaRest(userId, resource, action, tenantId);
    }

    /**
     * gRPC-based permission check — ~4x faster than REST (Protobuf + HTTP/2).
     */
    private boolean checkViaGrpc(String userId, String resource, String action, String tenantId) {
        log.debug("gRPC permission check: user={}, resource={}, action={}", userId, resource, action);

        PermissionCheckResponse response = permissionStub
                .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .checkPermission(PermissionCheckRequest.newBuilder()
                        .setUserId(userId)
                        .setResource(resource)
                        .setAction(action)
                        .setTenantId(tenantId != null ? tenantId : "")
                        .build());

        log.debug("gRPC permission result: user={}, allowed={}, reason={}",
                userId, response.getAllowed(), response.getDecisionReason());
        return response.getAllowed();
    }

    /**
     * REST fallback for permission check — used when gRPC is disabled or fails.
     */
    private boolean checkViaRest(String userId, String resource, String action, String tenantId) {
        log.debug("REST permission check: user={}, resource={}, action={}", userId, resource, action);

        try {
            WebClient.RequestBodySpec request = authWebClient.post()
                    .uri("/auth/api/v1/permissions/check")
                    .contentType(MediaType.APPLICATION_JSON);

            // Pass tenant context to auth-service for DB routing
            if (tenantId != null && !tenantId.isBlank()) {
                request = request.header("X-Tenant-Id", tenantId);
            }

            Boolean allowed = request
                    .bodyValue(Map.of(
                            "userId", userId,
                            "resource", resource,
                            "action", action))
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            boolean result = Boolean.TRUE.equals(allowed);
            log.debug("REST permission result: user={}, allowed={}", userId, result);
            return result;

        } catch (Exception e) {
            log.error("REST permission check failed: user={}, resource={}, action={}, error={}",
                    userId, resource, action, e.getMessage());
            // Fail closed: deny access on error
            return false;
        }
    }
}
