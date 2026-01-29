package com.learning.backendservice.repository;

import com.learning.backendservice.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {
    
    /**
     * Find entry by tenant and key.
     */
    Optional<Entry> findByTenantIdAndKey(String tenantId, String key);

    /**
     * Find all entries for a tenant.
     */
    List<Entry> findAllByTenantId(String tenantId);

    /**
     * Check if key exists for tenant.
     */
    boolean existsByTenantIdAndKey(String tenantId, String key);

    /**
     * Find entry by ID and tenant.
     */
    Optional<Entry> findByTenantIdAndId(String tenantId, Long id);

    /**
     * Delete entry by tenant and key.
     */
    void deleteByTenantIdAndKey(String tenantId, String key);
}
