package com.learning.platformservice.apikey.repository;

import com.learning.platformservice.apikey.entity.ApiKey;
import com.learning.platformservice.apikey.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find an active API key by its hash (for validation).
     */
    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);

    /**
     * Find all API keys for a tenant (for listing).
     */
    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Find all active API keys for a tenant.
     */
    List<ApiKey> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, ApiKeyStatus status);

    /**
     * Count active API keys for a tenant (for limit enforcement).
     */
    long countByTenantIdAndStatus(String tenantId, ApiKeyStatus status);

    /**
     * Update last used timestamp and increment usage count.
     */
    @Modifying
    @Query("UPDATE ApiKey a SET a.lastUsedAt = :now, a.usageCount = a.usageCount + 1 WHERE a.id = :id")
    void updateUsage(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Mark expired keys (scheduled job).
     */
    @Modifying
    @Query("UPDATE ApiKey a SET a.status = 'EXPIRED' WHERE a.status = 'ACTIVE' AND a.expiresAt < :now")
    int expireOldKeys(@Param("now") Instant now);
}
