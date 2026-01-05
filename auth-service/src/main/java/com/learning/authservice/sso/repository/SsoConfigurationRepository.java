package com.learning.authservice.sso.repository;

import com.learning.authservice.sso.entity.SsoConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for SSO configuration.
 * Uses tenant-specific database via TenantDataSourceRouter.
 */
@Repository
public interface SsoConfigurationRepository extends JpaRepository<SsoConfiguration, Long> {

    /**
     * Find SSO configuration by tenant ID.
     */
    Optional<SsoConfiguration> findByTenantId(String tenantId);

    /**
     * Check if SSO is configured for a tenant.
     */
    boolean existsByTenantId(String tenantId);

    /**
     * Delete SSO configuration for a tenant.
     */
    void deleteByTenantId(String tenantId);
}
