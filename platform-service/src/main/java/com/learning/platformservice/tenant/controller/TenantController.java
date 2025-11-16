package com.learning.platformservice.tenant.controller;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.service.TenantProvisioningService;
import com.learning.platformservice.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final TenantProvisioningService tenantProvisioningService;

    public TenantController(TenantService tenantService, TenantProvisioningService tenantProvisioningService) {
        this.tenantService = tenantService;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @Operation(summary = "Provision tenant", description = "Creates a new tenant with schema or database mode")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant provisioned"),
            @ApiResponse(responseCode = "409", description = "Tenant already exists"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping
    public ResponseEntity<TenantDto> provision(@Valid @RequestBody ProvisionTenantRequest request) {
        return ResponseEntity.ok(tenantProvisioningService.provision(request));
    }

    @Operation(summary = "Provision tenant", description = "Creates a new tenant with schema or database mode")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant provisioned"),
            @ApiResponse(responseCode = "409", description = "Tenant already exists"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TenantDto> get(@PathVariable String id) {
        return tenantService.getTenant(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Retry tenant migration", description = "Retries migration for a tenant in MIGRATION_ERROR state; no-op otherwise")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Migration retried or no-op"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PostMapping("/{id}/retry-migration")
    public ResponseEntity<TenantDto> retryMigration(@PathVariable String id) {
        return ResponseEntity.ok(tenantProvisioningService.retryMigration(id));
    }
}
