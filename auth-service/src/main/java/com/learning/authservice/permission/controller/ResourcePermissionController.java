package com.learning.authservice.permission.controller;

import com.learning.authservice.permission.dto.ResourceAccessResponse;
import com.learning.authservice.permission.dto.RevokePermissionRequest;
import com.learning.authservice.permission.dto.SharePermissionRequest;
import com.learning.authservice.user.repository.TenantUserRepository;
import com.learning.common.infra.exception.TooManyRequestsException;
import com.learning.common.infra.openfga.OpenFgaReader;
import com.learning.common.infra.openfga.OpenFgaTupleService;
import com.learning.common.infra.ratelimit.ApiRateLimiter;
import com.learning.common.infra.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing fine-grained permissions via OpenFGA.
 * 
 * <p>
 * This controller is only active when openfga.enabled=true.
 * It provides endpoints to share access, revoke access, and list who has
 * access to specific resources.
 * 
 * <p>
 * Security Features:
 * <ul>
 * <li>Rate limiting: 10 requests per second per user (configurable)</li>
 * <li>User validation: Target user must exist in tenant before granting</li>
 * <li>Owner authorization: Only resource owners can revoke access</li>
 * </ul>
 * 
 * <p>
 * Endpoints:
 * <ul>
 * <li>POST /api/v1/permissions/share - Grant access to a resource</li>
 * <li>DELETE /api/v1/permissions/revoke - Revoke access from a resource</li>
 * <li>GET /api/v1/permissions/{type}/{id} - List access grants</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/resource-permissions")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class ResourcePermissionController {

        private final OpenFgaTupleService tupleService;
        private final TenantUserRepository userRepository;
        private final ApiRateLimiter rateLimiter;
        private final OpenFgaReader fgaReader;

        private static final String ENDPOINT_SHARE = "permission-share";
        private static final String ENDPOINT_REVOKE = "permission-revoke";
        private static final String ENDPOINT_LIST = "permission-list";

        /**
         * Grant a user access to a specific resource.
         * 
         * Security:
         * - Rate limited to prevent abuse
         * - Target user must exist in tenant (S3)
         * 
         * @param userId   Grantor's user ID (from header)
         * @param tenantId Tenant context (from header)
         * @param request  Share request with target user, resource, relation
         * @return Success response with message
         */
        @PostMapping("/share")
        @RequirePermission(resource = "permission", action = "manage")
        public ResponseEntity<Map<String, String>> shareAccess(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestHeader("X-Tenant-Id") String tenantId,
                        @Valid @RequestBody SharePermissionRequest request) {

                // G4: Rate limiting
                if (!rateLimiter.tryAcquire(ENDPOINT_SHARE, userId)) {
                        throw new TooManyRequestsException(ENDPOINT_SHARE, userId);
                }

                // S3: Validate target user exists in tenant
                validateUserExists(request.targetUserId());

                log.info("Sharing access: grantor={} grantee={} resource={}:{} relation={}",
                                userId, request.targetUserId(), request.resourceType(), request.resourceId(),
                                request.relation());

                tupleService.grantAccess(
                                request.targetUserId(),
                                request.relation(),
                                request.resourceType(),
                                request.resourceId());

                return ResponseEntity.ok(Map.of(
                                "status", "success",
                                "message", String.format("Granted %s access to %s:%s for user %s",
                                                request.relation(), request.resourceType(), request.resourceId(),
                                                request.targetUserId())));
        }

        /**
         * Revoke a user's access from a specific resource.
         * 
         * Security:
         * - Rate limited to prevent abuse
         * - Only resource owners can revoke access (S2)
         * 
         * @param userId   Revoker's user ID (from header)
         * @param tenantId Tenant context (from header)
         * @param request  Revoke request with target user, resource, relation
         * @return Success response with message
         */
        @DeleteMapping("/revoke")
        @RequirePermission(resource = "permission", action = "manage")
        public ResponseEntity<Map<String, String>> revokeAccess(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestHeader("X-Tenant-Id") String tenantId,
                        @Valid @RequestBody RevokePermissionRequest request) {

                // G4: Rate limiting
                if (!rateLimiter.tryAcquire(ENDPOINT_REVOKE, userId)) {
                        throw new TooManyRequestsException(ENDPOINT_REVOKE, userId);
                }

                // S2: Verify user has owner relation before allowing revoke
                validateOwnerPermission(userId, request.resourceType(), request.resourceId());

                log.info("Revoking access: revoker={} target={} resource={}:{} relation={}",
                                userId, request.targetUserId(), request.resourceType(), request.resourceId(),
                                request.relation());

                tupleService.revokeAccess(
                                request.targetUserId(),
                                request.relation(),
                                request.resourceType(),
                                request.resourceId());

                return ResponseEntity.ok(Map.of(
                                "status", "success",
                                "message", String.format("Revoked %s access to %s:%s from user %s",
                                                request.relation(), request.resourceType(), request.resourceId(),
                                                request.targetUserId())));
        }

        /**
         * List all access grants for a specific resource.
         * 
         * @param userId       Requester's user ID (from header)
         * @param tenantId     Tenant context (from header)
         * @param resourceType Type of resource (folder, document, etc.)
         * @param resourceId   Resource identifier
         * @return List of access grants
         */
        @GetMapping("/{resourceType}/{resourceId}")
        @RequirePermission(resource = "permission", action = "read")
        public ResponseEntity<ResourceAccessResponse> listAccess(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestHeader("X-Tenant-Id") String tenantId,
                        @PathVariable String resourceType,
                        @PathVariable String resourceId) {

                // G4: Rate limiting
                if (!rateLimiter.tryAcquire(ENDPOINT_LIST, userId)) {
                        throw new TooManyRequestsException(ENDPOINT_LIST, userId);
                }

                log.info("Listing access: requester={} resource={}:{}", userId, resourceType, resourceId);

                List<ResourceAccessResponse.AccessGrant> grants = tupleService.listTuples(resourceType, resourceId)
                                .stream()
                                .map(tuple -> new ResourceAccessResponse.AccessGrant(tuple.userId(), tuple.relation()))
                                .toList();

                return ResponseEntity.ok(new ResourceAccessResponse(resourceType, resourceId, grants));
        }

        // ========================================================================
        // Security Validations
        // ========================================================================

        /**
         * S3: Validate that target user exists in the current tenant.
         * Prevents granting permissions to non-existent users.
         */
        private void validateUserExists(String targetUserId) {
                // Check if user exists by ID
                boolean exists = userRepository.existsById(targetUserId);

                if (!exists) {
                        log.warn("Attempted to grant permission to non-existent user: {}", targetUserId);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Target user does not exist in this organization");
                }
        }

        /**
         * S2: Verify user has owner relation before allowing revoke.
         * Only owners can revoke access grants.
         */
        private void validateOwnerPermission(String userId, String resourceType, String resourceId) {
                // Check if user has owner relation via OpenFGA
                boolean isOwner = fgaReader.check(userId, "owner", resourceType, resourceId);

                // Also allow if user has can_share permission (delegated management)
                boolean canShare = fgaReader.check(userId, "can_share", resourceType, resourceId);

                if (!isOwner && !canShare) {
                        log.warn("User {} attempted to revoke access without owner/share permission on {}:{}",
                                        userId, resourceType, resourceId);
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "You must be the owner to revoke access");
                }
        }
}
