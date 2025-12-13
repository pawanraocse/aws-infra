package com.learning.common.infra.tenant;

import com.learning.common.dto.TenantDbConfig;
import com.learning.common.util.SimpleCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Default implementation of TenantRegistryService that fetches tenant DB config
 * from platform-service via HTTP.
 * 
 * All services needing tenant DB routing should use this implementation
 * (or provide their own if needed).
 */
@Slf4j
@RequiredArgsConstructor
public class PlatformServiceTenantRegistry implements TenantRegistryService {

    private final WebClient platformWebClient;
    private final TenantLocalCache localCache;

    @Override
    public TenantDbConfig load(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Invalid tenant id");
        }

        return localCache.get(tenantId, () -> fetchTenantDbInfo(tenantId));
    }

    private TenantDbConfig fetchTenantDbInfo(String tenantId) {
        try {
            log.debug("Fetching DB config for tenant: {}", tenantId);

            return platformWebClient.get()
                    .uri("/internal/tenants/{tenantId}/db-info", tenantId)
                    .retrieve()
                    .bodyToMono(TenantDbConfig.class)
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofMillis(300)))
                    .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("Tenant not found: " + tenantId)))
                    .map(info -> {
                        String decryptedPassword = info.password() != null
                                ? SimpleCryptoUtil.decrypt(info.password())
                                : null;
                        return new TenantDbConfig(info.jdbcUrl(), info.username(), decryptedPassword);
                    })
                    .blockOptional()
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        } catch (Throwable e) {
            log.error("Error fetching tenant config for tenant {}: {}", tenantId, e.getMessage());
            throw new RuntimeException("Failed to load tenant DB config: " + e.getMessage(), e);
        }
    }
}
