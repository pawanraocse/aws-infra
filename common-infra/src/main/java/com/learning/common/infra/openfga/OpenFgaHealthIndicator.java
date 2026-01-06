package com.learning.common.infra.openfga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for OpenFGA service.
 * 
 * Reports:
 * - UP: OpenFGA is reachable and circuit breaker is closed
 * - DEGRADED: Circuit breaker is half-open (recovering)
 * - DOWN: OpenFGA is unreachable or circuit breaker is open
 * 
 * Exposed via /actuator/health when actuator is enabled.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
public class OpenFgaHealthIndicator implements HealthIndicator {

    private final OpenFgaProperties properties;
    private final OpenFgaResilienceConfig resilience;

    @Override
    public Health health() {
        try {
            // Check circuit breaker state first (cheap check)
            String circuitState = resilience.getCircuitState();
            var metrics = resilience.getMetrics();

            if (resilience.isCircuitOpen()) {
                return Health.down()
                        .withDetail("status", "Circuit breaker OPEN - OpenFGA unavailable")
                        .withDetail("circuitState", circuitState)
                        .withDetail("failureRate", metrics.failureRate() + "%")
                        .withDetail("failedCalls", metrics.failedCalls())
                        .build();
            }

            // Circuit is closed or half-open
            if ("HALF_OPEN".equals(circuitState)) {
                return Health.status("DEGRADED")
                        .withDetail("status", "Recovering - testing OpenFGA connectivity")
                        .withDetail("circuitState", circuitState)
                        .withDetail("apiUrl", properties.getApiUrl())
                        .build();
            }

            // Circuit is closed - OpenFGA is healthy
            return Health.up()
                    .withDetail("status", "OpenFGA operational")
                    .withDetail("circuitState", circuitState)
                    .withDetail("apiUrl", properties.getApiUrl())
                    .withDetail("mode", "store-per-tenant")
                    .withDetail("successfulCalls", metrics.successfulCalls())
                    .build();

        } catch (Exception e) {
            log.warn("OpenFGA health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("status", "Health check failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
