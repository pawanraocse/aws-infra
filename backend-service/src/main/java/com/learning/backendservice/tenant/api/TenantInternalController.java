package com.learning.backendservice.tenant.api;

import com.learning.backendservice.tenant.migration.TenantMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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
    private final TenantRegistryService tenantRegistryService; // Placeholder, will implement or integrate.

    @PostMapping("/{tenantId}/migrate")
    public ResponseEntity<MigrationResult> migrateTenant(@PathVariable String tenantId) {
        TenantDbInfo dbInfo = tenantRegistryService.load(tenantId);
        String lastVersion = migrationService.migrateTenant(dbInfo.jdbcUrl, dbInfo.username, dbInfo.password);
        return ResponseEntity.ok(new MigrationResult(lastVersion));
    }

    // Placeholder DTOs and service interface for compilation; to be implemented with platform client.
    public interface TenantRegistryService {
        TenantDbInfo load(String tenantId);
    }

    @Value
    public static class TenantDbInfo {
        String jdbcUrl;
        String username;
        String password;
    }

    @Value
    public static class MigrationResult {
        String lastVersion;
    }
}

