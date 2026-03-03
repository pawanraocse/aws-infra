package com.learning.platformservice.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.common.dto.TenantDeletedEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Consumes tenant deletion events from the cleanup SQS queue (via SNS fanout).
 * Drops the tenant's dedicated database if it's an ORG tenant.
 * Personal tenants use shared schema and are skipped.
 */
@Component
@ConditionalOnProperty(name = "app.async-deletion.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TenantCleanupConsumer {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @SqsListener("tenant-cleanup")
    public void handleTenantDeleted(String message) {
        try {
            TenantDeletedEvent event = objectMapper.readValue(message, TenantDeletedEvent.class);
            log.info("Received TenantDeletedEvent: tenantId={}, tenantType={}",
                    event.tenantId(), event.tenantType());

            // Only drop DB for ORG tenants (dedicated database)
            if (!"ORGANIZATION".equals(event.tenantType())) {
                log.info("Skipping DB drop for non-ORG tenant: tenantId={}, type={}",
                        event.tenantId(), event.tenantType());
                return;
            }

            if (event.dbUrl() == null || event.dbUrl().isBlank()) {
                log.warn("No DB URL for tenant, skipping DB drop: tenantId={}", event.tenantId());
                return;
            }

            dropTenantDatabase(event);

        } catch (Exception e) {
            log.error("Failed to process TenantDeletedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Tenant cleanup failed", e);
        }
    }

    private void dropTenantDatabase(TenantDeletedEvent event) {
        // Extract database name from JDBC URL: jdbc:postgresql://host:port/dbname
        String dbName = extractDatabaseName(event.dbUrl());
        if (dbName == null) {
            log.error("Could not extract database name from URL: {}", event.dbUrl());
            return;
        }

        log.info("Dropping tenant database: tenantId={}, dbName={}", event.tenantId(), dbName);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Terminate active connections to the tenant database
            stmt.execute(String.format(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s' AND pid <> pg_backend_pid()",
                    dbName));

            // Drop the database
            stmt.execute(String.format("DROP DATABASE IF EXISTS \"%s\"", dbName));

            log.info("Successfully dropped tenant database: tenantId={}, dbName={}",
                    event.tenantId(), dbName);

        } catch (Exception e) {
            log.error("Failed to drop tenant database: tenantId={}, dbName={}, error={}",
                    event.tenantId(), dbName, e.getMessage(), e);
            throw new RuntimeException("Failed to drop tenant database: " + dbName, e);
        }
    }

    /**
     * Extract database name from JDBC URL.
     * Handles: jdbc:postgresql://host:port/dbname?params
     */
    static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        try {
            // Remove jdbc: prefix and parse as URI-like
            String withoutPrefix = jdbcUrl.replaceFirst("^jdbc:", "");
            // Find last / before ? or end
            int lastSlash = withoutPrefix.lastIndexOf('/');
            if (lastSlash < 0) return null;
            String dbPart = withoutPrefix.substring(lastSlash + 1);
            // Remove query params
            int queryStart = dbPart.indexOf('?');
            if (queryStart >= 0) {
                dbPart = dbPart.substring(0, queryStart);
            }
            return dbPart.isBlank() ? null : dbPart;
        } catch (Exception e) {
            return null;
        }
    }
}
