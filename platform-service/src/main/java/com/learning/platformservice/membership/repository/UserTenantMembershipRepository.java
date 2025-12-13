package com.learning.platformservice.membership.repository;

import com.learning.platformservice.membership.entity.MembershipStatus;
import com.learning.platformservice.membership.entity.UserTenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user-tenant membership operations.
 * 
 * <p>
 * Provides methods for querying memberships by email, tenant, and
 * managing the multi-tenant login flow.
 * </p>
 */
@Repository
public interface UserTenantMembershipRepository extends JpaRepository<UserTenantMembership, UUID> {

    // ========== Email-based Queries (for login flow) ==========

    /**
     * Find all active memberships for a user by email (case-insensitive).
     * Used during login to show tenant selector.
     */
    @Query("""
            SELECT m FROM UserTenantMembership m
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.status = :status
            ORDER BY m.isDefault DESC, m.lastAccessedAt DESC NULLS LAST
            """)
    List<UserTenantMembership> findByEmailAndStatus(
            @Param("email") String email,
            @Param("status") MembershipStatus status);

    /**
     * Find all active memberships for a user by email.
     * Convenience method using ACTIVE status.
     */
    default List<UserTenantMembership> findActiveByEmail(String email) {
        return findByEmailAndStatus(email, MembershipStatus.ACTIVE);
    }

    /**
     * Find a specific membership by email and tenant.
     */
    @Query("""
            SELECT m FROM UserTenantMembership m
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.tenantId = :tenantId
            """)
    Optional<UserTenantMembership> findByEmailAndTenant(
            @Param("email") String email,
            @Param("tenantId") String tenantId);

    /**
     * Find the default membership for a user.
     */
    @Query("""
            SELECT m FROM UserTenantMembership m
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.isDefault = true
            AND m.status = 'ACTIVE'
            """)
    Optional<UserTenantMembership> findDefaultByEmail(@Param("email") String email);

    /**
     * Find all tenants owned by a user.
     */
    @Query("""
            SELECT m FROM UserTenantMembership m
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.isOwner = true
            AND m.status = 'ACTIVE'
            """)
    List<UserTenantMembership> findOwnedByEmail(@Param("email") String email);

    /**
     * Count active memberships for a user.
     */
    @Query("""
            SELECT COUNT(m) FROM UserTenantMembership m
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.status = 'ACTIVE'
            """)
    long countActiveByEmail(@Param("email") String email);

    // ========== Tenant-based Queries ==========

    /**
     * Find all active members of a tenant.
     */
    @Query("""
            SELECT m FROM UserTenantMembership m
            WHERE m.tenantId = :tenantId
            AND m.status = 'ACTIVE'
            ORDER BY m.isOwner DESC, m.roleHint, m.joinedAt
            """)
    List<UserTenantMembership> findActiveByTenant(@Param("tenantId") String tenantId);

    /**
     * Find the owner of a tenant.
     */
    @Query("""
            SELECT m FROM UserTenantMembership m
            WHERE m.tenantId = :tenantId
            AND m.isOwner = true
            AND m.status = 'ACTIVE'
            """)
    Optional<UserTenantMembership> findOwnerByTenant(@Param("tenantId") String tenantId);

    /**
     * Count active members in a tenant.
     */
    @Query("SELECT COUNT(m) FROM UserTenantMembership m WHERE m.tenantId = :tenantId AND m.status = 'ACTIVE'")
    long countActiveByTenant(@Param("tenantId") String tenantId);

    // ========== Cognito ID Queries ==========

    /**
     * Find memberships by Cognito user ID.
     */
    List<UserTenantMembership> findByCognitoUserId(String cognitoUserId);

    /**
     * Update Cognito user ID for all memberships of an email.
     */
    @Modifying
    @Query("""
            UPDATE UserTenantMembership m
            SET m.cognitoUserId = :cognitoUserId
            WHERE LOWER(m.userEmail) = LOWER(:email)
            """)
    int updateCognitoIdByEmail(
            @Param("email") String email,
            @Param("cognitoUserId") String cognitoUserId);

    // ========== Modification Operations ==========

    /**
     * Update last accessed timestamp.
     */
    @Modifying
    @Query("UPDATE UserTenantMembership m SET m.lastAccessedAt = :timestamp WHERE m.id = :id")
    int updateLastAccessed(@Param("id") UUID id, @Param("timestamp") OffsetDateTime timestamp);

    /**
     * Clear default flag for all memberships of a user except the specified one.
     */
    @Modifying
    @Query("""
            UPDATE UserTenantMembership m
            SET m.isDefault = false
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.id != :exceptId
            AND m.isDefault = true
            """)
    int clearDefaultExcept(@Param("email") String email, @Param("exceptId") UUID exceptId);

    /**
     * Soft delete all memberships for a tenant (when tenant is deleted).
     */
    @Modifying
    @Query("UPDATE UserTenantMembership m SET m.status = 'REMOVED' WHERE m.tenantId = :tenantId")
    int removeAllByTenant(@Param("tenantId") String tenantId);

    // ========== Existence Checks ==========

    /**
     * Check if a membership exists for email and tenant.
     */
    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM UserTenantMembership m
            WHERE LOWER(m.userEmail) = LOWER(:email)
            AND m.tenantId = :tenantId
            """)
    boolean existsByEmailAndTenant(@Param("email") String email, @Param("tenantId") String tenantId);

    /**
     * Check if user has any PERSONAL tenant ownership.
     */
    @Query(value = """
            SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
            FROM user_tenant_memberships m
            JOIN tenant t ON m.tenant_id = t.id
            WHERE LOWER(m.user_email) = LOWER(:email)
            AND m.is_owner = true
            AND m.status = 'ACTIVE'
            AND t.tenant_type = 'PERSONAL'
            """, nativeQuery = true)
    boolean hasPersonalTenant(@Param("email") String email);
}
