package com.learning.platformservice.sqs;

import com.learning.common.dto.ProvisionTenantEvent;
import com.learning.platformservice.tenant.service.TenantProvisioningService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SQS consumer for async tenant provisioning.
 * Listens to the tenant-provisioning queue and executes the full provisioning
 * action chain (StorageProvisionAction → MigrationInvokeAction → AuditLogAction)
 * on the already-created tenant row.
 *
 * Only active when async provisioning is enabled.
 * Retries are handled by SQS visibility timeout + DLQ (max 3 retries).
 */
@Component
@ConditionalOnProperty(name = "app.async-provision.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SqsProvisioningConsumer {

    private final TenantProvisioningService tenantProvisioningService;

    /**
     * Handle provisioning events from SQS.
     * The tenant row already exists with PROVISIONING status.
     * This method runs the full action chain (storage, migration, audit)
     * and transitions status to ACTIVE on success, or PROVISION_ERROR/MIGRATION_ERROR on failure.
     */
    @SqsListener("tenant-provisioning")
    public void handleProvisionEvent(ProvisionTenantEvent event) {
        log.info("sqs_provision_received tenantId={} type={}", event.tenantId(), event.tenantType());
        long start = System.currentTimeMillis();

        try {
            tenantProvisioningService.executeProvisioningActions(event.tenantId());
            log.info("sqs_provision_completed tenantId={} durationMs={}",
                    event.tenantId(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("sqs_provision_failed tenantId={} error={} durationMs={}",
                    event.tenantId(), e.getMessage(), System.currentTimeMillis() - start, e);
            // Re-throw to let SQS retry (visibility timeout) or send to DLQ
            throw e;
        }
    }
}
