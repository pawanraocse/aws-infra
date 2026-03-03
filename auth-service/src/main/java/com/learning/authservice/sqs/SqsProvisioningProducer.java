package com.learning.authservice.sqs;

import com.learning.common.dto.ProvisionTenantEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SQS producer for async tenant provisioning.
 * Sends ProvisionTenantEvent to the tenant-provisioning queue.
 * Only active when async provisioning is enabled.
 */
@Component
@ConditionalOnProperty(name = "app.async-provision.enabled", havingValue = "true")
@Slf4j
public class SqsProvisioningProducer {

    private static final String QUEUE_NAME = "tenant-provisioning";
    private final SqsTemplate sqsTemplate;

    public SqsProvisioningProducer(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    /**
     * Send a provisioning event to the SQS queue.
     *
     * @param event the provisioning event
     */
    public void sendProvisionEvent(ProvisionTenantEvent event) {
        log.info("sqs_send_provision_event tenantId={} type={}", event.tenantId(), event.tenantType());
        try {
            sqsTemplate.send(to -> to
                    .queue(QUEUE_NAME)
                    .payload(event));
            log.info("sqs_provision_event_sent tenantId={}", event.tenantId());
        } catch (Exception e) {
            log.error("sqs_provision_event_failed tenantId={} error={}", event.tenantId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send provisioning event for tenant: " + event.tenantId(), e);
        }
    }
}
