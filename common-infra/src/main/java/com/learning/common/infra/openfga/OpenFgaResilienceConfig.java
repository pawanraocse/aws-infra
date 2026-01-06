package com.learning.common.infra.openfga;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Provides resilience patterns (retry, circuit breaker) for OpenFGA operations.
 * 
 * Configuration:
 * - Retry: 3 attempts with exponential backoff (100ms, 200ms, 400ms)
 * - Circuit Breaker: Opens after 5 failures in 10 calls, half-open after 30s
 * 
 * Usage:
 * - Wrap FGA SDK calls: resilience.executeWithResilience("check", () ->
 * fgaClient.check(...))
 * - Falls back to default value on persistent failure
 * 
 * SOLID: Single Responsibility - only handles resilience patterns
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaResilienceConfig {

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public OpenFgaResilienceConfig() {
        // Configure Retry: 3 attempts with exponential backoff
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class) // Don't retry validation errors
                .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("openfga");

        // Configure Circuit Breaker
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open when 50% of calls fail
                .minimumNumberOfCalls(5) // Need at least 5 calls to calculate
                .slidingWindowSize(10) // Consider last 10 calls
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Stay open for 30s
                .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 test calls
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        this.circuitBreaker = cbRegistry.circuitBreaker("openfga");

        // Log state transitions
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("OpenFGA Circuit Breaker state change: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));

        log.info("OpenFGA resilience configured: retry=3 attempts, circuit breaker=50% threshold");
    }

    /**
     * Execute a supplier with retry and circuit breaker protection.
     * 
     * @param operationName Name for logging (e.g., "check", "writeTuple")
     * @param supplier      The operation to execute
     * @param fallback      Value to return if all retries fail or circuit is open
     * @return Result of supplier or fallback
     */
    public <T> T executeWithResilience(String operationName, Supplier<T> supplier, T fallback) {
        try {
            // Compose: Circuit Breaker wraps Retry
            Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, supplier));

            return decoratedSupplier.get();
        } catch (Exception e) {
            log.warn("OpenFGA {} failed after retries (circuit: {}): {}",
                    operationName, circuitBreaker.getState(), e.getMessage());
            return fallback;
        }
    }

    /**
     * Execute a runnable with retry and circuit breaker protection.
     * Used for write operations that don't return a value.
     */
    public void executeWithResilience(String operationName, Runnable runnable) {
        try {
            Runnable decoratedRunnable = CircuitBreaker.decorateRunnable(circuitBreaker,
                    Retry.decorateRunnable(retry, runnable));

            decoratedRunnable.run();
        } catch (Exception e) {
            log.warn("OpenFGA {} failed after retries (circuit: {}): {}",
                    operationName, circuitBreaker.getState(), e.getMessage());
            // Write operations fail silently (already logged)
        }
    }

    /**
     * Check if circuit breaker is open (FGA unavailable).
     */
    public boolean isCircuitOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * Get current circuit breaker state for health checks.
     */
    public String getCircuitState() {
        return circuitBreaker.getState().name();
    }

    /**
     * Get circuit breaker metrics for monitoring.
     */
    public CircuitBreakerMetrics getMetrics() {
        var metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerMetrics(
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls());
    }

    public record CircuitBreakerMetrics(
            String state,
            float failureRate,
            int successfulCalls,
            int failedCalls) {
    }
}
