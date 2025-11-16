package com.learning.platformservice.tenant.action;

import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Invokes downstream service migrations (phase 2 of provisioning) after storage is created.
 * Currently only backend-service; future services can be added by composing calls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(50) // After StorageProvisionAction, before AuditLogAction, etc.
public class MigrationInvokeAction implements TenantProvisionAction {

    private final WebClient backendWebClient; // configured bean pointing at backend-service base URL

    @Override
    public void execute(TenantProvisionContext context) throws TenantProvisioningException {
        String tenantId = context.getTenant().getId();
        try {
            // Call backend-service internal migration endpoint
            String lastVersion = backendWebClient.post()
                    .uri("/internal/tenants/{tenantId}/migrate", tenantId)
                    .retrieve()
                    .bodyToMono(MigrationResult.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .onErrorResume(e -> {
                        log.error("tenant_migration_backend_failed tenantId={} error={}", tenantId, e.getMessage());
                        return Mono.error(new TenantProvisioningException(tenantId, "Backend migration failed: " + e.getMessage(), e));
                    })
                    .map(MigrationResult::lastVersion)
                    .block();
            context.setLastMigrationVersion(lastVersion);
            log.info("tenant_migration_backend_success tenantId={} version={}", tenantId, lastVersion);
        } catch (TenantProvisioningException ex) {
            throw ex; // already wrapped
        } catch (Exception e) {
            throw new TenantProvisioningException(tenantId, "Migration invoke failure: " + e.getMessage(), e);
        }
    }

    // Local DTO for deserialization; matches backend internal controller response
    public record MigrationResult(String lastVersion) { }
}

