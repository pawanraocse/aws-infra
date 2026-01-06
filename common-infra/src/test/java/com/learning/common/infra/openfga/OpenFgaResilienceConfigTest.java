package com.learning.common.infra.openfga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OpenFGA resilience configuration.
 * Tests retry logic and circuit breaker behavior.
 */
class OpenFgaResilienceConfigTest {

    private OpenFgaResilienceConfig resilience;

    @BeforeEach
    void setUp() {
        resilience = new OpenFgaResilienceConfig();
    }

    // ========================================================================
    // Retry Tests
    // ========================================================================

    @Test
    @DisplayName("Should return result on successful execution")
    void executeWithResilience_Success() {
        // Given
        boolean result = resilience.executeWithResilience("check", () -> true, false);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return fallback when operation fails")
    void executeWithResilience_FailsToFallback() {
        // Given - operation always throws
        boolean result = resilience.executeWithResilience("check", () -> {
            throw new RuntimeException("FGA unavailable");
        }, false);

        // Then - should return fallback (false)
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should retry and eventually succeed")
    void executeWithResilience_RetriesAndSucceeds() {
        // Given - operation fails twice then succeeds
        int[] attempts = { 0 };
        boolean result = resilience.executeWithResilience("check", () -> {
            attempts[0]++;
            if (attempts[0] < 3) {
                throw new RuntimeException("Transient failure");
            }
            return true;
        }, false);

        // Then - should succeed after retries
        assertThat(result).isTrue();
        assertThat(attempts[0]).isEqualTo(3);
    }

    // ========================================================================
    // Circuit Breaker Tests
    // ========================================================================

    @Test
    @DisplayName("Circuit should start closed")
    void circuitBreaker_StartsInClosedState() {
        assertThat(resilience.isCircuitOpen()).isFalse();
        assertThat(resilience.getCircuitState()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Should track metrics")
    void circuitBreaker_TracksMetrics() {
        // Given - some successful calls
        for (int i = 0; i < 3; i++) {
            resilience.executeWithResilience("check", () -> true, false);
        }

        // Then - metrics should reflect success
        var metrics = resilience.getMetrics();
        assertThat(metrics.state()).isEqualTo("CLOSED");
        assertThat(metrics.successfulCalls()).isEqualTo(3);
        assertThat(metrics.failedCalls()).isEqualTo(0);
    }

    @Test
    @DisplayName("Circuit should open after repeated failures")
    void circuitBreaker_OpensAfterFailures() {
        // Given - simulate enough failures to open circuit
        // Default config: 50% failure rate with minimum 5 calls
        for (int i = 0; i < 10; i++) {
            resilience.executeWithResilience("check", () -> {
                throw new RuntimeException("Persistent failure");
            }, false);
        }

        // Then - circuit should be open
        assertThat(resilience.isCircuitOpen()).isTrue();
        assertThat(resilience.getCircuitState()).isEqualTo("OPEN");
    }

    // ========================================================================
    // Runnable Tests
    // ========================================================================

    @Test
    @DisplayName("Runnable should execute successfully")
    void executeRunnable_Success() {
        // Given
        boolean[] executed = { false };

        // When
        resilience.executeWithResilience("writeTuple", () -> executed[0] = true);

        // Then
        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("Runnable should not throw on failure")
    void executeRunnable_FailsSilently() {
        // Given - this should not throw
        resilience.executeWithResilience("writeTuple", () -> {
            throw new RuntimeException("Write failed");
        });

        // No exception = success
    }
}
