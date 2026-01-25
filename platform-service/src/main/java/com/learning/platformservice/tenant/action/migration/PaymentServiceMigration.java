package com.learning.platformservice.tenant.action.migration;

import com.learning.common.dto.MigrationResult;
import com.learning.common.dto.TenantDbConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@RequiredArgsConstructor
@Slf4j
public class PaymentServiceMigration implements ServiceMigrationStrategy {

    private final WebClient paymentWebClient;

    @Override
    public String serviceName() {
        return "payment-service";
    }

    @Override
    public MigrationResult migrate(String tenantId, TenantDbConfig dbConfig) {
        log.info("Triggering payment-service migration for tenant: {}", tenantId);

        // Payment service uses a shared DB, so it manages its own connection.
        // We trigger the migration endpoint to ensure the DB/Table exists.
        // It does not use the tenant-specific dbConfig.

        return paymentWebClient.post()
                .uri("/payment-service/api/v1/payment/internal/migrate")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(MigrationResult.class)
                .block();
    }
}
