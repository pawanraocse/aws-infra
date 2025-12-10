package com.learning.authservice.tenant;

import com.learning.authservice.cache.LocalCache;
import com.learning.common.dto.TenantDbConfig;
import com.learning.common.util.SimpleCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Fetches tenant database configuration from platform-service.
 * Caches results to minimize inter-service calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceTenantRegistry {

    private final WebClient platformWebClient;
    private final LocalCache localCache;

    /**
     * Load tenant database configuration.
     * 
     * @param tenantId The tenant identifier
     * @return TenantDbConfig with decrypted password
     * @throws IllegalArgumentException if tenant not found
     */
    public TenantDbConfig load(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Invalid tenant id");
        }

        return localCache.get(tenantId, () -> fetchTenantDbInfo(tenantId));
    }

    private TenantDbConfig fetchTenantDbInfo(String tenantId) {
        log.debug("Fetching tenant DB config for tenant: {}", tenantId);

        return platformWebClient.get()
                .uri("/platform/internal/tenants/{tenantId}/db-info", tenantId)
                .retrieve()
                .bodyToMono(TenantDbConfig.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofMillis(300)))
                .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("Tenant not found: " + tenantId)))
                .map(info -> {
                    String decryptedPassword = info.password() != null
                            ? SimpleCryptoUtil.decrypt(info.password())
                            : null;
                    log.debug("Successfully loaded DB config for tenant: {}", tenantId);
                    return new TenantDbConfig(info.jdbcUrl(), info.username(), decryptedPassword);
                })
                .blockOptional()
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }

    /**
     * Clear cache for a specific tenant (useful for testing or tenant updates).
     */
    public void evict(String tenantId) {
        localCache.evict(tenantId);
        log.info("Evicted tenant DB config from cache: {}", tenantId);
    }
}
