package com.learning.backendservice.controller;

import com.learning.backendservice.dto.MigrationResult;
import com.learning.backendservice.tenant.migration.TenantMigrationService;
import com.learning.backendservice.tenant.registry.PlatformServiceTenantRegistry;
import com.learning.common.dto.TenantDbInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tenants")
@RequiredArgsConstructor
@Slf4j
public class TenantInternalController {

    private final TenantMigrationService migrationService;
    private final PlatformServiceTenantRegistry tenantRegistryService;

    @PostMapping("/{tenantId}/migrate")
    public ResponseEntity<MigrationResult> migrateTenant(@PathVariable String tenantId) {
        TenantDbInfo dbInfo = tenantRegistryService.load(tenantId);
        String lastVersion = migrationService.migrateTenant(dbInfo.jdbcUrl(), dbInfo.username(), dbInfo.password());
        return ResponseEntity.ok(new MigrationResult(lastVersion));
    }
}

