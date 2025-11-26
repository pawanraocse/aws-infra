package com.learning.platformservice.tenant.api;

import com.learning.common.dto.TenantDbInfo;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tenants")
@RequiredArgsConstructor
public class TenantRegistryController {
    private final TenantRepository tenantRepository;

    @GetMapping("/{tenantId}/db-info")
    public ResponseEntity<TenantDbInfo> getTenantDbInfo(@PathVariable String tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> ResponseEntity.ok(new TenantDbInfo(
                        t.getJdbcUrl(),
                        t.getDbUserSecretRef(),
                        t.getDbUserPasswordEnc())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
