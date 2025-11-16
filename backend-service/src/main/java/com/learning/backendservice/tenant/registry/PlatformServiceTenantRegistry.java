package com.learning.backendservice.tenant.registry;

import com.learning.backendservice.cache.LocalCache;
import com.learning.common.dto.TenantDbInfo;
import com.learning.common.util.SimpleCryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlatformServiceTenantRegistry implements TenantRegistryService {

    private final WebClient platformWebClient;
    private final LocalCache localCache;

    @Override
    public TenantDbInfo load(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Invalid tenant id");
        }

        return localCache.get(tenantId, () -> fetchTenantDbInfo(tenantId));
    }

    private TenantDbInfo fetchTenantDbInfo(String tenantId) {
        return platformWebClient.get()
                .uri("/internal/tenants/{tenantId}/db-info", tenantId)
                .retrieve()
                .bodyToMono(TenantDbInfo.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofMillis(300)))
                .switchIfEmpty(Mono.error(() ->
                        new IllegalArgumentException("Tenant not found: " + tenantId)))
                .map(info -> {
                    String decryptedPassword = info.password() != null
                            ? SimpleCryptoUtil.decrypt(info.password())
                            : null;
                    return new TenantDbInfo(info.jdbcUrl(), info.username(), decryptedPassword);
                })
                .blockOptional()
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }
}
