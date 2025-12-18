package com.learning.authservice.authorization.repository;

import com.learning.authservice.authorization.domain.AclEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ACL entries.
 */
@Repository
public interface AclEntryRepository extends JpaRepository<AclEntry, UUID> {

    /**
     * Find all ACL entries for a specific resource.
     */
    List<AclEntry> findByResourceId(UUID resourceId);

    /**
     * Find all ACL entries for a specific user.
     */
    List<AclEntry> findByPrincipalTypeAndPrincipalId(String principalType, String principalId);

    /**
     * Find ACL entry for a specific user and resource.
     */
    Optional<AclEntry> findByResourceIdAndPrincipalTypeAndPrincipalId(
            UUID resourceId, String principalType, String principalId);

    /**
     * Find valid (non-expired) ACL entries for a user.
     */
    @Query("SELECT a FROM AclEntry a WHERE a.principalType = :principalType " +
            "AND a.principalId = :principalId " +
            "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    List<AclEntry> findValidEntriesForPrincipal(
            @Param("principalType") String principalType,
            @Param("principalId") String principalId,
            @Param("now") Instant now);

    /**
     * Check if user has access to a resource (for permission check).
     */
    @Query("SELECT a FROM AclEntry a WHERE a.resourceId = :resourceId " +
            "AND a.principalType = :principalType " +
            "AND a.principalId = :principalId " +
            "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    Optional<AclEntry> findValidEntry(
            @Param("resourceId") UUID resourceId,
            @Param("principalType") String principalType,
            @Param("principalId") String principalId,
            @Param("now") Instant now);

    /**
     * Delete all ACL entries for a resource (when resource is deleted).
     */
    void deleteByResourceId(UUID resourceId);
}
