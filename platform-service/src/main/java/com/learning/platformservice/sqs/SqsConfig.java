package com.learning.platformservice.sqs;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

/**
 * AWS messaging configuration for platform-service.
 * Provides SQS template (provisioning) and SNS client (deletion fanout).
 */
@Configuration
public class SqsConfig {

    @Bean
    @ConditionalOnProperty(name = "app.async-provision.enabled", havingValue = "true")
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.async-deletion.enabled", havingValue = "true")
    public SnsClient snsClient(
            @Value("${spring.cloud.aws.sns.endpoint:}") String endpoint,
            @Value("${spring.cloud.aws.sns.region.static:${spring.cloud.aws.region.static:us-east-1}}") String region) {
        var builder = SnsClient.builder()
                .region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
