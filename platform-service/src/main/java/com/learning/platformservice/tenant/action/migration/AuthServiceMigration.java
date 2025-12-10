package com.learning.platformservice.tenant.action.migration;

import com.learning.common.dto.MigrationResult;
import com.learning.common.dto.TenantDbConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@RequiredArgsConstructor
public class AuthServiceMigration implements ServiceMigrationStrategy {
    private final WebClient webClient;

    @Override
    public String serviceName() {
        return "auth-service";
    }

    @Override
    public MigrationResult migrate(String tenantId, TenantDbConfig config) {
        return webClient.post()
                .uri("/auth/internal/tenants/{tenantId}/migrate", tenantId)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(MigrationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }
}
