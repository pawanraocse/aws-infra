package com.learning.platformservice.tenant.service;

import com.learning.common.dto.TenantDeletedEvent;
import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.sqs.SnsPublisher;
import com.learning.platformservice.tenant.entity.DeletedAccount;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.entity.TenantStatus;
import com.learning.platformservice.tenant.exception.TenantNotFoundException;
import com.learning.platformservice.tenant.repo.DeletedAccountRepository;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 * 5. Publish TenantDeletedEvent to SNS for async DB cleanup (if enabled)
 */
@Service
@Slf4j
public class TenantDeletionService {

    private final TenantRepository tenantRepository;
    private final DeletedAccountRepository deletedAccountRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final SnsPublisher snsPublisher; // null when async-deletion is disabled

    public TenantDeletionService(
            TenantRepository tenantRepository,
            DeletedAccountRepository deletedAccountRepository,
            UserTenantMembershipRepository membershipRepository,
            ObjectProvider<SnsPublisher> snsPublisherProvider) {
        this.tenantRepository = tenantRepository;
        this.deletedAccountRepository = deletedAccountRepository;
        this.membershipRepository = membershipRepository;
        this.snsPublisher = snsPublisherProvider.getIfAvailable();
    }

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

            // 5. Publish to SNS for async cleanup (DB drop, S3, etc.)
            publishDeletionEvent(tenant, deletedBy);

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

    /**
     * Publish tenant deletion event to SNS for async cleanup (DB drop, S3, etc.).
     * No-op when async-deletion feature flag is disabled.
     */
    private void publishDeletionEvent(Tenant tenant, String deletedBy) {
        if (snsPublisher == null) {
            log.debug("SNS publisher not available (async-deletion disabled), skipping event publish");
            return;
        }

        TenantDeletedEvent event = new TenantDeletedEvent(
                tenant.getId(),
                tenant.getTenantType().name(),
                tenant.getJdbcUrl(),
                tenant.getOwnerEmail(),
                deletedBy,
                OffsetDateTime.now().toString());

        snsPublisher.publishTenantDeleted(event);
    }
}
