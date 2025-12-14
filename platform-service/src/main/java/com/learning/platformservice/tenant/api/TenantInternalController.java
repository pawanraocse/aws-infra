package com.learning.platformservice.tenant.api;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.repo.TenantRepository;
import com.learning.platformservice.tenant.service.TenantDeletionService;
import com.learning.platformservice.tenant.service.TenantProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal controller for service-to-service tenant operations.
 * Called by auth-service during signup/deletion flows.
 * This endpoint bypasses @RequirePermission since it's for internal service
 * calls.
 */
@RestController
@RequestMapping("/internal/tenants")
@Slf4j
@RequiredArgsConstructor
public class TenantInternalController {

        private final TenantProvisioningService tenantProvisioningService;
        private final TenantDeletionService tenantDeletionService;
        private final TenantRepository tenantRepository;

        @Operation(summary = "Provision tenant (internal)", description = "Internal endpoint for service-to-service calls. Creates a new tenant during signup flow.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tenant provisioned"),
                        @ApiResponse(responseCode = "409", description = "Tenant already exists"),
                        @ApiResponse(responseCode = "400", description = "Validation error")
        })
        @PostMapping
        public ResponseEntity<TenantDto> provisionInternal(@Valid @RequestBody ProvisionTenantRequest request) {
                log.info("Internal tenant provisioning request: tenantId={} type={}",
                                request.id(), request.tenantType());
                return ResponseEntity.ok(tenantProvisioningService.provision(request));
        }

        @Operation(summary = "Delete tenant (internal)", description = "Internal endpoint for account deletion. Drops tenant DB, marks as deleted.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tenant deleted"),
                        @ApiResponse(responseCode = "404", description = "Tenant not found"),
                        @ApiResponse(responseCode = "409", description = "Tenant already deleted or being deleted")
        })
        @DeleteMapping("/{tenantId}")
        public ResponseEntity<Map<String, String>> deleteInternal(
                        @PathVariable String tenantId,
                        @RequestHeader(value = "X-Deleted-By", required = false) String deletedBy) {
                log.info("Internal tenant deletion request: tenantId={}, deletedBy={}", tenantId, deletedBy);

                tenantDeletionService.deleteTenant(tenantId, deletedBy);

                return ResponseEntity.ok(Map.of(
                                "status", "DELETED",
                                "tenantId", tenantId,
                                "message", "Tenant deleted successfully"));
        }

        @Operation(summary = "Get tenant status (internal)", description = "Internal endpoint for Gateway to validate tenant status before routing requests.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tenant status returned"),
                        @ApiResponse(responseCode = "404", description = "Tenant not found")
        })
        @GetMapping("/{tenantId}/status")
        public ResponseEntity<Map<String, String>> getTenantStatus(@PathVariable String tenantId) {
                log.debug("Internal tenant status request: tenantId={}", tenantId);

                return tenantRepository.findById(tenantId)
                                .map(tenant -> ResponseEntity.ok(Map.of(
                                                "tenantId", tenant.getId(),
                                                "status", tenant.getStatus() != null ? tenant.getStatus() : "UNKNOWN")))
                                .orElse(ResponseEntity.notFound().build());
        }
}
