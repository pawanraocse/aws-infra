package com.learning.systemtests.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.learning.systemtests.config.TestConfig.*;

/**
 * Utility class for cleaning up test resources.
 * Deletes users from Cognito and cleans up database records.
 * 
 * <p>
 * Usage: Call registerForCleanup() during test, then cleanupAll() in @AfterAll
 * </p>
 */
public class CleanupHelper {

    private static final Logger log = LoggerFactory.getLogger(CleanupHelper.class);

    // Thread-local lists to track resources created during tests
    private static final List<String> usersToDelete = new ArrayList<>();
    private static final List<String> tenantsToDelete = new ArrayList<>();

    private static CognitoIdentityProviderClient cognitoClient;

    private CleanupHelper() {
    }

    /**
     * Register a user email for cleanup after tests.
     */
    public static synchronized void registerUserForCleanup(String email) {
        if (email != null && !email.isBlank()) {
            usersToDelete.add(email);
            log.debug("Registered user for cleanup: {}", email);
        }
    }

    /**
     * Register a tenant ID for database cleanup after tests.
     */
    public static synchronized void registerTenantForCleanup(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            tenantsToDelete.add(tenantId);
            log.debug("Registered tenant for cleanup: {}", tenantId);
        }
    }

    /**
     * Clean up all registered resources.
     * Should be called in @AfterAll of test classes.
     */
    public static synchronized void cleanupAll() {
        log.info("Starting cleanup: {} users, {} tenants", usersToDelete.size(), tenantsToDelete.size());

        // Clean up Cognito users
        for (String email : usersToDelete) {
            deleteUserFromCognito(email);
        }
        usersToDelete.clear();

        // Clean up database tenants
        for (String tenantId : tenantsToDelete) {
            deleteTenantFromDatabase(tenantId);
        }
        tenantsToDelete.clear();

        // Close Cognito client
        if (cognitoClient != null) {
            cognitoClient.close();
            cognitoClient = null;
        }

        log.info("✅ Cleanup complete");
    }

    /**
     * Delete a user from Cognito using Admin API.
     * Requires cognito-idp:AdminDeleteUser permission.
     */
    private static void deleteUserFromCognito(String email) {
        // Skip if User Pool ID is not configured
        if (COGNITO_USER_POOL_ID == null || COGNITO_USER_POOL_ID.isEmpty()) {
            log.debug("Cognito User Pool ID not configured, skipping user deletion: {}", email);
            return;
        }

        try {
            CognitoIdentityProviderClient client = getCognitoClient();
            if (client == null) {
                log.warn("Cognito client not available, skipping user deletion: {}", email);
                return;
            }

            client.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(COGNITO_USER_POOL_ID)
                    .username(email)
                    .build());

            log.info("✅ Deleted Cognito user: {}", email);
        } catch (UserNotFoundException e) {
            log.debug("User not found in Cognito (already deleted?): {}", email);
        } catch (Exception e) {
            log.warn("Failed to delete Cognito user {}: {}", email, e.getMessage());
        }
    }

    /**
     * Delete tenant records from the platform database.
     * Performs soft delete by setting deleted_at.
     */
    private static void deleteTenantFromDatabase(String tenantId) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            // Soft delete tenant record
            String sql = "UPDATE tenants SET deleted_at = NOW() WHERE tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    log.info("✅ Soft-deleted tenant from DB: {}", tenantId);
                } else {
                    log.debug("Tenant not found or already deleted: {}", tenantId);
                }
            }

            // Also clean up memberships
            String membershipSql = "UPDATE memberships SET deleted_at = NOW() WHERE tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement stmt = conn.prepareStatement(membershipSql)) {
                stmt.setString(1, tenantId);
                int updated = stmt.executeUpdate();
                log.debug("Soft-deleted {} membership records for tenant: {}", updated, tenantId);
            }
        } catch (SQLException e) {
            log.warn("Failed to cleanup tenant {}: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Get or create the Cognito client.
     */
    private static CognitoIdentityProviderClient getCognitoClient() {
        if (cognitoClient == null) {
            try {
                cognitoClient = CognitoIdentityProviderClient.builder()
                        .region(Region.of(AWS_REGION))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            } catch (Exception e) {
                log.warn("Could not create Cognito client: {}", e.getMessage());
                return null;
            }
        }
        return cognitoClient;
    }

    /**
     * Hard delete tenant (for test data only, not production).
     * Use with caution.
     */
    public static void hardDeleteTenant(String tenantId) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD)) {
            // Delete memberships first (foreign key)
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM memberships WHERE tenant_id = ?")) {
                stmt.setString(1, tenantId);
                stmt.executeUpdate();
            }

            // Delete tenant
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM tenants WHERE tenant_id = ?")) {
                stmt.setString(1, tenantId);
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    log.info("✅ Hard-deleted tenant from DB: {}", tenantId);
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to hard-delete tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
