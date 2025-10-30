package com.learning.backendservice.repository;

import com.learning.backendservice.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByTenantId(String tenantId);

    Optional<Tenant> findBySchemaName(String schemaName);

    boolean existsByTenantId(String tenantId);
}
