package com.learning.backendservice.controller;

import com.learning.backendservice.dto.TenantRequestDto;
import com.learning.backendservice.dto.TenantResponseDto;
import com.learning.backendservice.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Administration", description = "Tenant provisioning and management")
public class TenantAdminController {

    private final TenantService tenantService;

    @Operation(summary = "Create new tenant",
            description = "Provisions a new tenant with isolated schema. Requires ADMIN role.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TenantResponseDto> createTenant(@Valid @RequestBody TenantRequestDto request) {
        log.info("Creating tenant: {} - {}", request.getTenantId(), request.getTenantName());
        TenantResponseDto tenant = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @Operation(summary = "Get tenant information", description = "Retrieves tenant details by ID")
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponseDto> getTenant(
            @Parameter(description = "Tenant ID") @PathVariable String tenantId) {
        TenantResponseDto tenant = tenantService.getTenant(tenantId);
        return ResponseEntity.ok(tenant);
    }
}
