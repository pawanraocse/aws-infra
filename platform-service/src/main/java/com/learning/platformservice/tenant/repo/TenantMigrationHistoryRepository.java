package com.learning.platformservice.tenant.repo;

import com.learning.platformservice.tenant.entity.TenantMigrationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantMigrationHistoryRepository extends JpaRepository<TenantMigrationHistory, UUID> {
}

