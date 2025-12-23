package com.learning.systemtests.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/**
 * Centralized configuration for system tests.
 * All URLs, paths, and credentials are defined here for consistency.
 * 
 * <p>
 * AWS Cognito configuration is fetched from SSM Parameter Store.
 * </p>
 */
public final class TestConfig {

    private static final Logger log = LoggerFactory.getLogger(TestConfig.class);

    private TestConfig() {
        // Utility class
    }

    // ============================================================
    // Service URLs
    // ============================================================

    /**
     * Gateway URL - ALL requests should go through Gateway in system tests.
     * This tests the real production routing.
     */
    public static final String GATEWAY_URL = getEnv("GATEWAY_URL", "http://localhost:8080");

    /**
     * Eureka URL - for service discovery verification.
     */
    public static final String EUREKA_URL = getEnv("EUREKA_URL", "http://localhost:8761");

    // ============================================================
    // API Paths (relative to Gateway)
    // ============================================================

    /**
     * Auth API base path.
     * Auth-service context-path is /auth, controller is /api/v1/auth
     */
    public static final String AUTH_API = "/auth/api/v1/auth";

    /**
     * Invitation API base path.
     */
    public static final String INVITATION_API = "/auth/api/v1/invitations";

    /**
     * Roles API base path.
     */
    public static final String ROLES_API = "/auth/api/v1/roles";

    /**
     * User management API base path.
     */
    public static final String USERS_API = "/auth/api/v1/users";

    /**
     * Account API base path (for delete account, etc.).
     */
    public static final String ACCOUNT_API = "/auth/api/v1/account";

    /**
     * Entries API base path (backend-service).
     * Backend-service context-path is /, so just /api/v1/entries
     */
    public static final String ENTRIES_API = "/api/v1/entries";

    /**
     * Platform API base path.
     * Platform-service context-path is /platform
     */
    public static final String PLATFORM_API = "/platform/api/v1";

    /**
     * Organizations API base path.
     */
    public static final String ORGANIZATIONS_API = "/platform/api/v1/organizations";

    // ============================================================
    // Database Configuration
    // ============================================================

    public static final String DB_HOST = getEnv("DB_HOST", "localhost");
    public static final int DB_PORT = Integer.parseInt(getEnv("DB_PORT", "5432"));
    public static final String DB_NAME = getEnv("DB_NAME", "awsinfra");
    public static final String DB_USER = getEnv("DB_USER", "postgres");
    public static final String DB_PASSWORD = getEnv("DB_PASSWORD", "postgres");

    /**
     * Platform database URL (master database).
     */
    public static final String PLATFORM_DB_URL = String.format(
            "jdbc:postgresql://%s:%d/%s", DB_HOST, DB_PORT, DB_NAME);

    // ============================================================
    // Test Timeouts
    // ============================================================

    /**
     * Maximum time to wait for service to be ready (seconds).
     */
    public static final int SERVICE_READY_TIMEOUT_SECONDS = 60;

    /**
     * Interval between service readiness checks (milliseconds).
     */
    public static final int SERVICE_READY_POLL_INTERVAL_MS = 2000;

    /**
     * Default API timeout (milliseconds).
     */
    public static final int API_TIMEOUT_MS = 30000;

    // ============================================================
    // Test User Configuration
    // ============================================================

    /**
     * Default test password that meets Cognito requirements.
     */
    public static final String DEFAULT_TEST_PASSWORD = "TestPassword123!";

    // ============================================================
    // AWS Cognito Configuration (from SSM Parameter Store)
    // ============================================================

    /**
     * AWS Region for all AWS services.
     */
    public static final String AWS_REGION = getEnv("AWS_REGION", "us-east-1");

    /**
     * Project name for SSM parameter path.
     */
    public static final String PROJECT_NAME = getEnv("PROJECT_NAME", "cloud-infra");

    /**
     * Environment for SSM parameter path.
     */
    public static final String ENVIRONMENT = getEnv("ENVIRONMENT", "dev");

    /**
     * Cognito User Pool ID - fetched from SSM Parameter Store.
     * Path: /${PROJECT_NAME}/${ENVIRONMENT}/cognito/user_pool_id
     */
    public static final String COGNITO_USER_POOL_ID = getCognitoUserPoolId();

    // ============================================================
    // Helper Methods
    // ============================================================

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Get tenant database URL for a specific tenant.
     */
    public static String getTenantDbUrl(String tenantId) {
        // Tenant databases follow naming convention: t_<tenant_id> with hyphens
        // replaced
        String dbName = "t_" + tenantId.replace("-", "_");
        return String.format("jdbc:postgresql://%s:%d/%s", DB_HOST, DB_PORT, dbName);
    }

    /**
     * Fetch Cognito User Pool ID from SSM Parameter Store.
     * Falls back to environment variable if SSM is unavailable.
     */
    private static String getCognitoUserPoolId() {
        // First check environment variable
        String envValue = System.getenv("COGNITO_USER_POOL_ID");
        if (envValue != null && !envValue.isEmpty()) {
            log.info("Using COGNITO_USER_POOL_ID from environment variable");
            return envValue;
        }

        // Fetch from SSM Parameter Store
        String paramPath = String.format("/%s/%s/cognito/user_pool_id", PROJECT_NAME, ENVIRONMENT);
        try {
            SsmClient ssmClient = SsmClient.builder()
                    .region(Region.of(AWS_REGION))
                    .build();

            GetParameterResponse response = ssmClient.getParameter(
                    GetParameterRequest.builder()
                            .name(paramPath)
                            .build());

            String value = response.parameter().value();
            log.info("Fetched COGNITO_USER_POOL_ID from SSM: {}", paramPath);
            ssmClient.close();
            return value;

        } catch (ParameterNotFoundException e) {
            log.warn("SSM parameter not found: {}. Using fallback.", paramPath);
            return ""; // Empty fallback - cleanup will be skipped
        } catch (Exception e) {
            log.warn("Failed to fetch SSM parameter {}: {}. Using fallback.", paramPath, e.getMessage());
            return ""; // Empty fallback - cleanup will be skipped
        }
    }
}
