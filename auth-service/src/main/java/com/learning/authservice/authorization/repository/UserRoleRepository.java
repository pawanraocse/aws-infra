package com.learning.authservice.authorization.repository;

import com.learning.authservice.authorization.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserRole entity.
 * Note: Tenant isolation is handled via TenantDataSourceRouter - all queries
 * automatically run against the current tenant's database.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

        /**
         * Find all roles for a user
         */
        List<UserRole> findByUserId(String userId);

        /**
         * Find all users with a specific role
         */
        List<UserRole> findByRoleId(String roleId);

        /**
         * Find a specific user role assignment
         */
        Optional<UserRole> findByUserIdAndRoleId(String userId, String roleId);

        /**
         * Check if user has a specific role
         */
        boolean existsByUserIdAndRoleId(String userId, String roleId);

        /**
         * Find all active (non-expired) roles for a user
         */
        @Query("""
                        SELECT ur FROM UserRole ur
                        WHERE ur.userId = :userId
                        AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
                        """)
        List<UserRole> findActiveRolesByUserId(
                        @Param("userId") String userId,
                        @Param("now") Instant now);

        /**
         * Delete all role assignments for a user
         */
        void deleteByUserId(String userId);

        /**
         * Delete a specific role assignment
         */
        void deleteByUserIdAndRoleId(String userId, String roleId);

        /**
         * Count users by role.
         * Returns list of [roleId, count] pairs for statistics.
         */
        @Query("SELECT ur.roleId, COUNT(DISTINCT ur.userId) FROM UserRole ur GROUP BY ur.roleId")
        List<Object[]> countUsersByRole();
}
