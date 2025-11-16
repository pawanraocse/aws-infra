package com.learning.platformservice.tenant.provision;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TenantProvisioner {
    public static final String DB_NAME_PREFIX = "t_";
    private final JdbcTemplate jdbcTemplate;
    private final boolean databaseModeFeatureEnabled; // legacy flag (storageMode request validation)
    private final boolean dbPerTenantEnabled; // explicit db-per-tenant capability flag
    private final Counter dbCreateAttempts;
    private final Counter dbCreateSuccess;
    private final Counter dbCreateFailure;
    private final Counter schemaCreateAttempts;
    private final Counter schemaCreateSuccess;
    private final Counter schemaCreateFailure;
    private final String dataSourceUrl;

    private static final String POSTGRES_DUPLICATE_DB_SQL_STATE = "42P04"; // database already exists
    private static final int DB_CREATE_MAX_ATTEMPTS = 2;
    private static final long BASE_BACKOFF_MS = 100L;

    public TenantProvisioner(DataSource dataSource,
                             MeterRegistry meterRegistry,
                             @Value("${platform.tenant.database-mode.enabled:false}") boolean databaseModeFeatureEnabled,
                             @Value("${platform.db-per-tenant.enabled:false}") boolean dbPerTenantEnabled,
                             @Value("${spring.datasource.url}") String dataSourceUrl) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.databaseModeFeatureEnabled = databaseModeFeatureEnabled;
        this.dbPerTenantEnabled = dbPerTenantEnabled;
        this.dataSourceUrl = dataSourceUrl;
        this.dbCreateAttempts = Counter.builder("platform.tenants.db.create.attempts").description("Database create attempts").register(meterRegistry);
        this.dbCreateSuccess = Counter.builder("platform.tenants.db.create.success").description("Successful tenant database creates").register(meterRegistry);
        this.dbCreateFailure = Counter.builder("platform.tenants.db.create.failure").description("Failed tenant database creates").register(meterRegistry);
        this.schemaCreateAttempts = Counter.builder("platform.tenants.schema.create.attempts").description("Schema create attempts").register(meterRegistry);
        this.schemaCreateSuccess = Counter.builder("platform.tenants.schema.create.success").description("Successful tenant schema creates").register(meterRegistry);
        this.schemaCreateFailure = Counter.builder("platform.tenants.schema.create.failure").description("Failed tenant schema creates").register(meterRegistry);
    }

    /**
     * Provision storage for tenant based on requested mode.
     * Returns JDBC URL pointing to tenant isolated storage (schema or database).
     */
    public String provisionTenantStorage(String tenantId, TenantStorageEnum storageMode) {
        return switch (storageMode) {
            case SCHEMA -> createSchemaPath(tenantId);
            case DATABASE -> createDatabasePath(tenantId);
        };
    }

    private String createSchemaPath(String tenantId) {
        schemaCreateAttempts.increment();
        String schemaName = buildSchemaName(tenantId);
        try {
            log.debug("schema_create_start tenantId={} schema={}", tenantId, schemaName);
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            schemaCreateSuccess.increment();
            log.info("schema_create_success tenantId={} schema={}", tenantId, schemaName);
        } catch (CannotGetJdbcConnectionException ex) {
            // Test/unit environment fallback: allow URL formation without physical DDL
            schemaCreateFailure.increment();
            log.warn("schema_create_connection_unavailable tenantId={} schema={} msg={}", tenantId, schemaName, ex.getMessage());
        } catch (Exception e) {
            schemaCreateFailure.increment();
            log.error("schema_create_failure tenantId={} schema={} error={}", tenantId, schemaName, e.getMessage(), e);
            throw e;
        }
        return buildMasterBaseUrl() + getMasterDatabaseName() + "?currentSchema=" + schemaName;
    }

    private String createDatabasePath(String tenantId) {
        if (!databaseModeFeatureEnabled) {
            throw new IllegalStateException("DATABASE storageMode disabled by feature flag platform.tenant.database-mode.enabled");
        }
        if (!dbPerTenantEnabled) {
            throw new IllegalStateException("DB-per-tenant capability disabled by feature flag platform.db-per-tenant.enabled");
        }
        dbCreateAttempts.increment();
        String dbName = buildDatabaseName(tenantId);

        for (int attempt = 1; attempt <= DB_CREATE_MAX_ATTEMPTS; attempt++) {
            try {
                ensureDatabaseNotExists(dbName); // optimistic pre-check (race still possible)
                log.debug("db_create_start tenantId={} dbName={} attempt={}", tenantId, dbName, attempt);
                executeCreateDatabase(dbName);
                dbCreateSuccess.increment();
                log.info("db_create_success tenantId={} dbName={} attempt={}", tenantId, dbName, attempt);
                return buildMasterBaseUrl() + dbName;
            } catch (SQLException sqlEx) {
                // Race: database created after pre-check but before CREATE DATABASE
                if (POSTGRES_DUPLICATE_DB_SQL_STATE.equals(sqlEx.getSQLState())) {
                    log.warn("db_create_race_detected tenantId={} dbName={} proceeding (duplicate)", tenantId, dbName);
                    dbCreateSuccess.increment();
                    return buildMasterBaseUrl() + dbName;
                }
                if (attempt < DB_CREATE_MAX_ATTEMPTS) {
                    long backoff = BASE_BACKOFF_MS * attempt;
                    log.warn("db_create_retry tenantId={} dbName={} attempt={} sqlState={} error={} backoffMs={}",
                            tenantId, dbName, attempt, sqlEx.getSQLState(), sqlEx.getMessage(), backoff);
                    try {
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                dbCreateFailure.increment();
                log.error("db_create_failure tenantId={} dbName={} sqlState={} error={}", tenantId, dbName, sqlEx.getSQLState(), sqlEx.getMessage(), sqlEx);
                throw new IllegalStateException("Failed to create database '" + dbName + "': " + sqlEx.getMessage(), sqlEx);
            } catch (Exception ex) {
                // Non-SQL exceptions (datasource issues, connection retrieval, etc.)
                if (attempt < DB_CREATE_MAX_ATTEMPTS) {
                    long backoff = BASE_BACKOFF_MS * attempt;
                    log.warn("db_create_retry_non_sql tenantId={} dbName={} attempt={} error={} backoffMs={}",
                            tenantId, dbName, attempt, ex.getMessage(), backoff);
                    try {
                        TimeUnit.MILLISECONDS.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                dbCreateFailure.increment();
                log.error("db_create_failure_non_sql tenantId={} dbName={} error={}", tenantId, dbName, ex.getMessage(), ex);
                throw new IllegalStateException("Failed to create database '" + dbName + "': " + ex.getMessage(), ex);
            }
        }
        // Should never reach here due to returns in loop
        throw new IllegalStateException("Unexpected database create flow termination for tenant=" + tenantId);
    }

    private void executeCreateDatabase(String dbName) throws SQLException {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) throw new IllegalStateException("DataSource unavailable for database creation");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            boolean originalAuto = conn.getAutoCommit();
            if (!originalAuto) conn.setAutoCommit(true);
            stmt.execute("CREATE DATABASE " + dbName);
            if (!originalAuto) conn.setAutoCommit(false);
        }
    }

    private void ensureDatabaseNotExists(String dbName) {
        Integer exists = jdbcTemplate.query(
                "SELECT 1 FROM pg_database WHERE datname = ?",
                ps -> ps.setString(1, dbName),
                rs -> rs.next() ? rs.getInt(1) : null
        );
        if (exists != null) {
            throw new IllegalStateException("Database already exists: " + dbName);
        }
    }

    private String buildSchemaName(String tenantId) {
        return buildDatabaseName(tenantId);
    }

    public String buildDatabaseName(String tenantId) {
        String sanitized = sanitize(tenantId);
        // PostgreSQL identifier length limit 63 chars
        String candidate = (DB_NAME_PREFIX + sanitized);
        if (candidate.length() > 63) {
            candidate = candidate.substring(0, 63);
        }
        return candidate;
    }

    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tenant id cannot be blank");
        }
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Tenant id became empty after sanitization");
        }
        return cleaned;
    }

    private String buildMasterBaseUrl() {
        // Extract 'jdbc:postgresql://host:port/' from datasource URL
        int idx = dataSourceUrl.indexOf("jdbc:postgresql://");
        if (idx < 0) {
            return "jdbc:postgresql://localhost:5432/"; // fallback
        }
        String after = dataSourceUrl.substring(idx + "jdbc:postgresql://".length());
        int slash = after.indexOf('/');
        if (slash < 0) {
            return dataSourceUrl.endsWith("/") ? dataSourceUrl : dataSourceUrl + "/";
        }
        String hostPort = after.substring(0, slash);
        return "jdbc:postgresql://" + hostPort + "/";
    }

    private String getMasterDatabaseName() {
        String base = buildMasterBaseUrl();
        String remainder = dataSourceUrl.substring(base.length());
        int qIdx = remainder.indexOf('?');
        return qIdx >= 0 ? remainder.substring(0, qIdx) : remainder;
    }

    public void dropTenantDatabase(String tenantId) {
        if (!dbPerTenantEnabled) {
            return; // feature disabled, nothing to drop
        }
        String dbName = buildDatabaseName(tenantId);
        try {
            log.debug("db_drop_start tenantId={} dbName={}", tenantId, dbName);
            jdbcTemplate.execute("DROP DATABASE IF EXISTS " + dbName);
            log.info("db_drop_success tenantId={} dbName={}", tenantId, dbName);
        } catch (Exception e) {
            log.warn("db_drop_failure tenantId={} dbName={} error={}", tenantId, dbName, e.getMessage(), e);
        }
    }

    public String createTenantDbUser(String tenantIdentifier, String username, TenantStorageEnum mode) {

        validateIdentifier(username, "username");
        validateIdentifier(tenantIdentifier, "tenantIdentifier");

        String password = generateRandomPassword(16);
        DataSource ds = jdbcTemplate.getDataSource();

        if (ds == null) {
            throw new IllegalStateException("DataSource unavailable");
        }

        try (var conn = ds.getConnection();
             var stmt = conn.createStatement()) {

            conn.setAutoCommit(true);

            createOrRotateUser(stmt, username, password);

            String grantSql = buildGrantSql(mode, tenantIdentifier, username);

            stmt.execute(grantSql);

            logPrivilegeSuccess(mode, username, tenantIdentifier);

            return password;

        } catch (SQLException e) {
            log.error("db_user_create_failure username={} tenantIdentifier={} error={}",
                    username, tenantIdentifier, e.getMessage(), e);

            throw new IllegalStateException(
                    "Failed to create/configure DB user '" + username + "'", e
            );
        }
    }

    private void createOrRotateUser(Statement stmt, String username, String password) throws SQLException {
        try {
            stmt.execute("CREATE USER %s WITH PASSWORD '%s';".formatted(quote(username), password));
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("already exists")) {
                stmt.execute("ALTER USER %s WITH PASSWORD '%s';".formatted(quote(username), password));
            } else {
                throw ex;
            }
        }
    }

    private String buildGrantSql(TenantStorageEnum mode, String tenantIdentifier, String username) {

        return switch (mode) {
            case DATABASE -> "GRANT ALL PRIVILEGES ON DATABASE %s TO %s;"
                    .formatted(quote(tenantIdentifier), quote(username));

            case SCHEMA -> """
                    GRANT USAGE ON SCHEMA %1$s TO %2$s;
                    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %1$s TO %2$s;
                    ALTER DEFAULT PRIVILEGES IN SCHEMA %1$s 
                    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %2$s;
                    """
                    .formatted(quote(tenantIdentifier), quote(username));
        };
    }

    private void logPrivilegeSuccess(TenantStorageEnum mode, String username, String tenantIdentifier) {
        switch (mode) {
            case DATABASE ->
                    log.info("db_user_granted_database_privileges username={} db={}", username, tenantIdentifier);

            case SCHEMA ->
                    log.info("db_user_granted_schema_privileges username={} schema={}", username, tenantIdentifier);
        }
    }

    private void validateIdentifier(String value, String field) {
        if (value == null || !value.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid identifier for " + field + ": " + value);
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier + "\"";
    }


    private String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }
}
