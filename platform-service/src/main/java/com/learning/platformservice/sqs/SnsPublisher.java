package com.learning.platformservice.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.common.dto.TenantDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Publishes tenant deletion events to SNS for fanout to cleanup subscribers.
 * Only active when async deletion is enabled.
 */
@Component
@ConditionalOnProperty(name = "app.async-deletion.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SnsPublisher {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${app.async-deletion.topic-arn}")
    private String topicArn;

    public void publishTenantDeleted(TenantDeletedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);

            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .build());

            log.info("Published TenantDeletedEvent to SNS: tenantId={}", event.tenantId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TenantDeletedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish tenant deletion event", e);
        } catch (Exception e) {
            log.error("Failed to publish TenantDeletedEvent to SNS: tenantId={}, error={}",
                    event.tenantId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish tenant deletion event", e);
        }
    }
}
