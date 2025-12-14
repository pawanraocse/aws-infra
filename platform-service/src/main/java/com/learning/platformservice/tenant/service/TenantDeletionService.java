package com.learning.platformservice.tenant.service;

import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.tenant.entity.DeletedAccount;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.entity.TenantStatus;
import com.learning.platformservice.tenant.exception.TenantNotFoundException;
import com.learning.platformservice.tenant.repo.DeletedAccountRepository;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Service for handling tenant deletion with soft-delete semantics.
 * 
 * Deletion flow:
 * 1. Set status = DELETING (atomic lock)
 * 2. Record in deleted_accounts for audit
 * 3. Mark all memberships as REMOVED
 * 4. Set status = DELETED, archivedAt = now
 * 
 * NOTE: Actual tenant DB is NOT dropped here. That will be handled by
 * an async cleanup job via SNS/SQS in Phase 6 (supports multi-DB: PostgreSQL,
 * MongoDB, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantDeletionService {

    private final TenantRepository tenantRepository;
    private final DeletedAccountRepository deletedAccountRepository;
    private final UserTenantMembershipRepository membershipRepository;

    /**
     * Soft-delete a tenant and mark all associated memberships as removed.
     * 
     * @param tenantId  Tenant to delete
     * @param deletedBy Email of user performing deletion
     * @throws TenantNotFoundException if tenant not found
     * @throws IllegalStateException   if tenant is already deleted or being deleted
     */
    @Transactional
    public void deleteTenant(String tenantId, String deletedBy) {
        log.info("Starting tenant deletion: tenantId={}, deletedBy={}", tenantId, deletedBy);

        // 1. Find tenant and validate
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        validateDeletionAllowed(tenant);

        // 2. Set status = DELETING (atomic lock)
        tenant.setStatus(TenantStatus.DELETING.name());
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);
        log.info("Tenant status set to DELETING: tenantId={}", tenantId);

        try {
            // 3. Record in deleted_accounts for audit
            createDeletionRecord(tenant, deletedBy);

            // 4. Mark all memberships as REMOVED
            markMembershipsRemoved(tenantId);

            // 5. Update tenant: status=DELETED, archivedAt=now()
            tenant.setStatus(TenantStatus.DELETED.name());
            tenant.setArchivedAt(OffsetDateTime.now());
            tenant.setUpdatedAt(OffsetDateTime.now());
            tenantRepository.save(tenant);

            // TODO: Publish to SNS for async cleanup (Phase 6)
            // - Drop tenant database (PostgreSQL)
            // - Drop tenant collections (MongoDB, future)
            // - Delete tenant S3 bucket/data
            // publishToSns("tenant.deleted", tenantId);

            log.info("Tenant deletion completed (soft-delete): tenantId={}", tenantId);

        } catch (Exception e) {
            log.error("Tenant deletion failed: tenantId={}, error={}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Tenant deletion failed: " + e.getMessage(), e);
        }
    }

    private void validateDeletionAllowed(Tenant tenant) {
        String status = tenant.getStatus();
        if (TenantStatus.DELETED.name().equals(status)) {
            throw new IllegalStateException("Tenant already deleted: " + tenant.getId());
        }
        if (TenantStatus.DELETING.name().equals(status)) {
            throw new IllegalStateException("Tenant deletion already in progress: " + tenant.getId());
        }
    }

    private void createDeletionRecord(Tenant tenant, String deletedBy) {
        DeletedAccount record = DeletedAccount.builder()
                .email(tenant.getOwnerEmail())
                .originalTenantId(tenant.getId())
                .tenantType(tenant.getTenantType().name())
                .tenantName(tenant.getName())
                .deletedAt(OffsetDateTime.now())
                .deletedBy(deletedBy)
                .build();

        deletedAccountRepository.save(record);
        log.info("Created deletion record: email={}, tenantId={}", tenant.getOwnerEmail(), tenant.getId());
    }

    private void markMembershipsRemoved(String tenantId) {
        int count = membershipRepository.removeAllByTenant(tenantId);
        log.info("Marked {} memberships as REMOVED for tenantId={}", count, tenantId);
    }

    /**
     * Check if a tenant is in a deletable state.
     */
    public boolean isDeletable(String tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> !TenantStatus.DELETED.name().equals(t.getStatus())
                        && !TenantStatus.DELETING.name().equals(t.getStatus()))
                .orElse(false);
    }
}
