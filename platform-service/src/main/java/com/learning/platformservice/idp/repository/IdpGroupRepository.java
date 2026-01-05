package com.learning.platformservice.idp.repository;

import com.learning.common.dto.IdpType;
import com.learning.platformservice.idp.entity.IdpGroup;
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
 * Repository for IdP Group entities.
 */
@Repository
public interface IdpGroupRepository extends JpaRepository<IdpGroup, UUID> {

    /**
     * Find all groups for a specific tenant.
     */
    List<IdpGroup> findByTenantId(String tenantId);

    /**
     * Find a group by tenant and external group ID.
     */
    Optional<IdpGroup> findByTenantIdAndExternalGroupId(String tenantId, String externalGroupId);

    /**
     * Find all groups for a tenant with a specific IdP type.
     */
    List<IdpGroup> findByTenantIdAndIdpType(String tenantId, IdpType idpType);

    /**
     * Find groups by external group IDs (for batch lookup during login).
     */
    @Query("SELECT g FROM IdpGroup g WHERE g.tenantId = :tenantId AND g.externalGroupId IN :groupIds")
    List<IdpGroup> findByTenantIdAndExternalGroupIdIn(
            @Param("tenantId") String tenantId,
            @Param("groupIds") List<String> groupIds);

    /**
     * Update the last synced timestamp for a group.
     */
    @Modifying
    @Query("UPDATE IdpGroup g SET g.lastSyncedAt = :syncTime WHERE g.id = :groupId")
    void updateLastSyncedAt(@Param("groupId") UUID groupId, @Param("syncTime") OffsetDateTime syncTime);

    /**
     * Count groups for a tenant.
     */
    long countByTenantId(String tenantId);

    /**
     * Delete all groups for a tenant (used when resetting SSO configuration).
     */
    void deleteByTenantId(String tenantId);
}
