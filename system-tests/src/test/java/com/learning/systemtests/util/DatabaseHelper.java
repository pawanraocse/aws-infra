package com.learning.systemtests.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.learning.systemtests.config.TestConfig.*;

/**
 * Utility class for database operations in system tests.
 * Provides methods for verification and cleanup of test data.
 */
public class DatabaseHelper {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

    private DatabaseHelper() {
        // Utility class
    }

    // ============================================================
    // Connection Methods
    // ============================================================

    /**
     * Get a connection to the platform (master) database.
     */
    public static Connection getPlatformConnection() throws SQLException {
        return DriverManager.getConnection(PLATFORM_DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Get a connection to a tenant database.
     */
    public static Connection getTenantConnection(String tenantId) throws SQLException {
        String url = getTenantDbUrl(tenantId);
        return DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
    }

    // ============================================================
    // Tenant Verification
    // ============================================================

    /**
     * Check if a tenant exists in the platform database.
     */
    public static boolean tenantExists(String tenantId) {
        String sql = "SELECT COUNT(*) FROM tenant WHERE id = ?";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error("Error checking tenant existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get tenant status from platform database.
     */
    public static String getTenantStatus(String tenantId) {
        String sql = "SELECT status FROM tenant WHERE id = ?";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("status") : null;
            }
        } catch (SQLException e) {
            log.error("Error getting tenant status: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a tenant database exists.
     */
    public static boolean tenantDatabaseExists(String tenantId) {
        String dbName = "t_" + tenantId.replace("-", "_");
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dbName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Error checking tenant database existence: {}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    // Membership Verification
    // ============================================================

    /**
     * Check if a user has a membership for a tenant.
     */
    public static boolean membershipExists(String email, String tenantId) {
        String sql = "SELECT COUNT(*) FROM user_tenant_memberships WHERE user_email = ? AND tenant_id = ?";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error("Error checking membership existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get all tenant IDs for a user email.
     */
    public static List<String> getTenantIdsForEmail(String email) {
        List<String> tenantIds = new ArrayList<>();
        String sql = "SELECT tenant_id FROM user_tenant_memberships WHERE user_email = ? AND status = 'ACTIVE'";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tenantIds.add(rs.getString("tenant_id"));
                }
            }
        } catch (SQLException e) {
            log.error("Error getting tenant IDs for email: {}", e.getMessage());
        }
        return tenantIds;
    }

    // ============================================================
    // Tenant Database Tables Verification
    // ============================================================

    /**
     * Check if a table exists in the tenant database.
     */
    public static boolean tableExistsInTenantDb(String tenantId, String tableName) {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?";
        try (Connection conn = getTenantConnection(tenantId);
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.warn("Error checking table in tenant DB (may not exist yet): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get row count from a table in tenant database.
     */
    public static int getRowCount(String tenantId, String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection conn = getTenantConnection(tenantId);
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            log.error("Error getting row count: {}", e.getMessage());
            return -1;
        }
    }

    // ============================================================
    // Cleanup Methods
    // ============================================================

    /**
     * Soft-delete a tenant for cleanup (sets status to DELETED).
     * Does NOT drop the database.
     */
    public static void softDeleteTenant(String tenantId) {
        String sql = "UPDATE tenant SET status = 'DELETED' WHERE id = ?";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenantId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                log.info("Soft-deleted tenant: {}", tenantId);
            }
        } catch (SQLException e) {
            log.error("Error soft-deleting tenant: {}", e.getMessage());
        }
    }

    /**
     * Remove all memberships for a tenant.
     */
    public static void removeMembershipsForTenant(String tenantId) {
        String sql = "UPDATE user_tenant_memberships SET status = 'REMOVED' WHERE tenant_id = ?";
        try (Connection conn = getPlatformConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenantId);
            int updated = stmt.executeUpdate();
            log.info("Removed {} memberships for tenant: {}", updated, tenantId);
        } catch (SQLException e) {
            log.error("Error removing memberships: {}", e.getMessage());
        }
    }

    /**
     * Clean up all data related to a test tenant.
     * Use this in @AfterAll to clean up test data.
     */
    public static void cleanupTestTenant(String tenantId) {
        log.info("Cleaning up test tenant: {}", tenantId);
        removeMembershipsForTenant(tenantId);
        softDeleteTenant(tenantId);
    }
}
