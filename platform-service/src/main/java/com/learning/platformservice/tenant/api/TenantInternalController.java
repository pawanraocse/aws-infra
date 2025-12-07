package com.learning.platformservice.tenant.api;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.service.TenantProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal controller for service-to-service tenant provisioning.
 * Called by auth-service during signup flow when there's no authenticated user
 * yet.
 * This endpoint bypasses @RequirePermission since it's for internal service
 * calls.
 */
@RestController
@RequestMapping("/internal/tenants")
@Slf4j
@RequiredArgsConstructor
public class TenantInternalController {

    private final TenantProvisioningService tenantProvisioningService;

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
}
