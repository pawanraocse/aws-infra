package com.learning.platformservice.tenant.api;

import com.learning.platformservice.tenant.dto.ProvisionTenantRequest;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantDto> provision(@Valid @RequestBody ProvisionTenantRequest request) {
        return ResponseEntity.ok(tenantService.provisionTenant(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDto> get(@PathVariable String id) {
        return tenantService.getTenant(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

