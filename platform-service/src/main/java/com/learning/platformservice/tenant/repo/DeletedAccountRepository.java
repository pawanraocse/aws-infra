package com.learning.platformservice.tenant.repo;

import com.learning.platformservice.tenant.entity.DeletedAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for deleted accounts audit records.
 */
@Repository
public interface DeletedAccountRepository extends JpaRepository<DeletedAccount, UUID> {

    /**
     * Find all deletion records for an email.
     * Used to check if user has previously deleted an account.
     */
    List<DeletedAccount> findByEmail(String email);

    /**
     * Check if email was previously deleted.
     */
    boolean existsByEmail(String email);

    /**
     * Find by original tenant ID.
     */
    List<DeletedAccount> findByOriginalTenantId(String tenantId);
}
