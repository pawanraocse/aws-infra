package com.learning.platformservice.tenant.controller;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.common.infra.security.RequirePermission;
import com.learning.common.infra.security.RoleLookupService;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.service.TenantProvisioningService;
import com.learning.platformservice.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Tenant management controller.
 * 
 * <p>
 * Role lookup is now done via RoleLookupService (database) instead of X-Role
 * header
 * for better security.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/tenants")
@Slf4j
public class TenantController {

    private final TenantService tenantService;
    private final TenantProvisioningService tenantProvisioningService;
    private final RoleLookupService roleLookupService;

    public TenantController(TenantService tenantService,
            TenantProvisioningService tenantProvisioningService,
            RoleLookupService roleLookupService) {
        this.tenantService = tenantService;
        this.tenantProvisioningService = tenantProvisioningService;
        this.roleLookupService = roleLookupService;
    }

    @Operation(summary = "List all tenants", description = "Lists all tenants in the system. Super-admin only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of all tenants"),
            @ApiResponse(responseCode = "403", description = "Not authorized - super-admin only")
    })
    @GetMapping
    public ResponseEntity<java.util.List<TenantDto>> listAll(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        // Direct super-admin check via database lookup
        if (!roleLookupService.isSuperAdmin(userId, tenantId)) {
            log.warn("Access denied to tenant list: userId={}", userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.debug("Super-admin listing all tenants: userId={}", userId);
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @Operation(summary = "Provision tenant", description = "Creates a new tenant with schema or database mode")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant provisioned"),
            @ApiResponse(responseCode = "409", description = "Tenant already exists"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping
    @RequirePermission(resource = "tenant", action = "create")
    public ResponseEntity<TenantDto> provision(@Valid @RequestBody ProvisionTenantRequest request) {
        return ResponseEntity.ok(tenantProvisioningService.provision(request));
    }

    @Operation(summary = "Get tenant by ID", description = "Gets tenant details. Users can read their own tenant, super-admin can read any.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TenantDto> get(
            @PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String requestTenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        // Super-admin can read any tenant (via database lookup)
        if (roleLookupService.isSuperAdmin(userId, requestTenantId)) {
            return tenantService.getTenant(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        // Users can only read their own tenant (from JWT)
        if (requestTenantId != null && requestTenantId.equals(id)) {
            return tenantService.getTenant(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        log.warn("Access denied to read tenant {}: userTenant={}, userId={}", id, requestTenantId, userId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @Operation(summary = "Retry tenant migration", description = "Retries migration for a tenant in MIGRATION_ERROR state; no-op otherwise")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Migration retried or no-op"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PostMapping("/{id}/retry-migration")
    @RequirePermission(resource = "tenant", action = "update")
    public ResponseEntity<TenantDto> retryMigration(@PathVariable String id) {
        return ResponseEntity.ok(tenantProvisioningService.retryMigration(id));
    }

    @Operation(summary = "Delete tenant", description = "Deletes (deprovisions) a tenant. Super-admin only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tenant deleted"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "403", description = "Not authorized - super-admin only")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        // Direct super-admin check via database lookup
        if (!roleLookupService.isSuperAdmin(userId, tenantId)) {
            log.warn("Access denied to delete tenant {}: userId={}", id, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        tenantProvisioningService.deprovision(id);
        return ResponseEntity.noContent().build();
    }
}
