package com.learning.platformservice.tenant.service;

import com.learning.common.dto.ProvisionTenantRequest;
import com.learning.common.dto.TenantType;
import com.learning.platformservice.membership.dto.AddMembershipRequest;
import com.learning.platformservice.membership.entity.MembershipRoleHint;
import com.learning.platformservice.membership.service.MembershipService;
import com.learning.platformservice.tenant.action.TenantProvisionAction;
import com.learning.platformservice.tenant.action.TenantProvisionContext;
import com.learning.platformservice.tenant.config.PlatformTenantProperties;
import com.learning.platformservice.tenant.dto.TenantDto;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.entity.TenantStatus;
import com.learning.platformservice.tenant.exception.TenantAlreadyExistsException;
import com.learning.platformservice.tenant.exception.TenantNotFoundException;
import com.learning.platformservice.tenant.exception.TenantProvisioningException;
import com.learning.platformservice.tenant.provision.TenantProvisioner;
import com.learning.platformservice.tenant.repo.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Orchestrates tenant provisioning: persist tenant row, create storage (via
 * actions), invoke downstream migrations, manage status transitions.
 */
@Service
@Primary
@Slf4j
public class TenantProvisioningServiceImpl implements TenantProvisioningService {
    private static final String MIGRATION_ACTION_CLASS = "MigrationInvokeAction";
    private final TenantRepository tenantRepository;
    private final Counter attemptsCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final List<TenantProvisionAction> actions;
    private final PlatformTenantProperties tenantProperties;
    private final TenantProvisioner tenantProvisioner;
    private final MembershipService membershipService;

    @Autowired
    public TenantProvisioningServiceImpl(TenantRepository tenantRepository,
            MeterRegistry meterRegistry,
            List<TenantProvisionAction> actions,
            PlatformTenantProperties tenantProperties,
            TenantProvisioner tenantProvisioner,
            MembershipService membershipService) {
        this.tenantRepository = tenantRepository;
        this.attemptsCounter = Counter.builder("platform.tenants.provision.attempts")
                .description("Tenant provision attempts").register(meterRegistry);
        this.successCounter = Counter.builder("platform.tenants.provision.success")
                .description("Successful tenant provisions").register(meterRegistry);
        this.failureCounter = Counter.builder("platform.tenants.provision.failure")
                .description("Failed tenant provisions").register(meterRegistry);
        this.actions = actions;
        this.tenantProperties = tenantProperties;
        this.tenantProvisioner = tenantProvisioner;
        this.membershipService = membershipService;
    }

    @Override
    public TenantDto provision(ProvisionTenantRequest request) {
        attemptsCounter.increment();
        String tenantId = request.id();

        Tenant tenant;
        java.util.Optional<Tenant> existingOpt = tenantRepository.findById(tenantId);

        if (existingOpt.isPresent()) {
            Tenant existing = existingOpt.get();
            if (!TenantStatus.DELETED.name().equals(existing.getStatus())) {
                throw new TenantAlreadyExistsException(tenantId);
            }
            // Reactivate existing tenant
            log.info("Reactivating deleted tenant: {}", tenantId);
            tenant = existing;
            // Keep original createdAt, but reset other fields logic continues below
        } else {
            tenant = new Tenant();
            tenant.setId(tenantId);
            tenant.setCreatedAt(OffsetDateTime.now());
        }

        tenant.setName(request.name());
        tenant.setStatus(TenantStatus.PROVISIONING.name());
        tenant.setStorageMode(request.storageMode());
        tenant.setSlaTier(request.slaTier());

        // NEW: Set tenant type and limits
        tenant.setTenantType(request.tenantType());
        tenant.setOwnerEmail(request.ownerEmail());
        tenant.setMaxUsers(request.maxUsers());

        // NEW: Set trial period for organization tenants
        if (request.tenantType() == TenantType.ORGANIZATION) {
            tenant.setTrialEndsAt(OffsetDateTime.now().plusDays(30));
            tenant.setSubscriptionStatus(com.learning.platformservice.tenant.entity.SubscriptionStatus.TRIAL);
        } else {
            // Reset if previously set (e.g. if switching types on reuse, though unlikely)
            tenant.setTrialEndsAt(null);
            tenant.setSubscriptionStatus(null);
        }

        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        long start = System.currentTimeMillis();
        TenantProvisionContext ctx = new TenantProvisionContext(request, tenant);
        try {
            for (TenantProvisionAction action : actions) {
                action.execute(ctx);
                if (tenant.getStatus().equals(TenantStatus.PROVISIONING.name()) && ctx.getJdbcUrl() != null) {
                    tenant.setStatus(TenantStatus.MIGRATING.name());
                    tenant.setUpdatedAt(OffsetDateTime.now());
                    tenantRepository.save(tenant);
                }
            }
        } catch (Exception e) {
            failureCounter.increment();
            // Differentiate migration phase failure
            if (tenant.getStatus().equals(TenantStatus.MIGRATING.name())) {
                tenant.setStatus(TenantStatus.MIGRATION_ERROR.name());
            } else {
                tenant.setStatus(TenantStatus.PROVISION_ERROR.name());
            }
            tenant.setUpdatedAt(OffsetDateTime.now());
            tenantRepository.save(tenant);
            if ("DATABASE".equalsIgnoreCase(request.storageMode()) && tenantProperties.isDropOnFailure()) {
                try {
                    tenantProvisioner.dropTenantDatabase(tenantId);
                } catch (Exception dropEx) {
                    log.warn("tenant_db_drop_failed tenantId={} error={}", tenantId, dropEx.getMessage(), dropEx);
                }
            }
            log.error("tenant_provision_failed tenantId={} phase={} error={}", tenantId, tenant.getStatus(),
                    e.getMessage(), e);
            throw new TenantProvisioningException(tenantId, "Failed provisioning: " + e.getMessage(), e);
        }
        tenant.setJdbcUrl(ctx.getJdbcUrl());
        tenant.setLastMigrationVersion(ctx.getLastMigrationVersion());
        tenant.setStatus(TenantStatus.ACTIVE.name());
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        // Create owner membership for multi-tenant login support
        if (request.ownerEmail() != null && !request.ownerEmail().isBlank()) {
            try {
                AddMembershipRequest membershipRequest = new AddMembershipRequest(
                        request.ownerEmail(),
                        null, // cognitoUserId - will be set on first login
                        tenantId,
                        MembershipRoleHint.OWNER.getValue(),
                        true, // isOwner
                        true, // isDefault (first membership for this user will be default)
                        null // invitedBy - owner is not invited
                );
                membershipService.addMembership(membershipRequest);
                log.info("tenant_owner_membership_created tenantId={} owner={}", tenantId, request.ownerEmail());
            } catch (Exception e) {
                // Membership creation failure should not block tenant provisioning
                // This can happen due to duplicate entries (idempotency) or other transient
                // issues
                log.warn("tenant_owner_membership_failed tenantId={} owner={} error={}",
                        tenantId, request.ownerEmail(), e.getMessage());
            }
        }

        successCounter.increment();
        log.info("tenant_provisioned tenantId={} type={} owner={} maxUsers={} storageMode={} durationMs={}",
                tenantId, request.tenantType(), request.ownerEmail(), request.maxUsers(),
                request.storageMode(), System.currentTimeMillis() - start);
        return new TenantDto(tenant.getId(), tenant.getName(),
                tenant.getTenantType() != null ? tenant.getTenantType().name() : "PERSONAL",
                tenant.getStatus(), tenant.getStorageMode(),
                tenant.getSlaTier(), tenant.getJdbcUrl(), tenant.getLastMigrationVersion());
    }

    @Override
    public TenantDto retryMigration(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (!TenantStatus.MIGRATION_ERROR.name().equals(tenant.getStatus())) {
            log.info("tenant_retry_migration_noop tenantId={} status={}", tenantId, tenant.getStatus());
            return new TenantDto(tenant.getId(), tenant.getName(),
                    tenant.getTenantType() != null ? tenant.getTenantType().name() : "PERSONAL",
                    tenant.getStatus(), tenant.getStorageMode(),
                    tenant.getSlaTier(), tenant.getJdbcUrl(), tenant.getLastMigrationVersion());
        }
        TenantProvisionContext ctx = new TenantProvisionContext(
                new com.learning.common.dto.ProvisionTenantRequest(
                        tenant.getId(),
                        tenant.getName(),
                        tenant.getStorageMode(),
                        tenant.getSlaTier(),
                        tenant.getTenantType(),
                        tenant.getOwnerEmail(),
                        tenant.getMaxUsers()),
                tenant);
        try {
            // Execute only migration-related actions (currently MigrationInvokeAction
            // identified by class name)
            actions.stream()
                    .filter(a -> a.getClass().getSimpleName().equals(MIGRATION_ACTION_CLASS))
                    .forEach(a -> {
                        try {
                            a.execute(ctx);
                        } catch (Exception e) {
                            throw new TenantProvisioningException(tenantId, "Migration retry failed: " + e.getMessage(),
                                    e);
                        }
                    });
        } catch (TenantProvisioningException e) {
            tenant.setStatus(TenantStatus.MIGRATION_ERROR.name());
            tenant.setUpdatedAt(OffsetDateTime.now());
            tenantRepository.save(tenant);
            log.error("tenant_retry_migration_failed tenantId={} error={}", tenantId, e.getMessage());
            throw e;
        }
        tenant.setLastMigrationVersion(ctx.getLastMigrationVersion());
        tenant.setStatus(TenantStatus.ACTIVE.name());
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);
        log.info("tenant_retry_migration_success tenantId={} version={}", tenantId, tenant.getLastMigrationVersion());
        return new TenantDto(tenant.getId(), tenant.getName(),
                tenant.getTenantType() != null ? tenant.getTenantType().name() : "PERSONAL",
                tenant.getStatus(), tenant.getStorageMode(),
                tenant.getSlaTier(), tenant.getJdbcUrl(), tenant.getLastMigrationVersion());
    }

    @Override
    public void deprovision(String tenantId) {
        log.info("tenant_deprovision_init tenantId={}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        // Soft delete: Mark as DELETED
        // Future: Actually drop database
        // (tenantProvisioner.dropTenantDatabase(tenantId))

        tenant.setStatus("DELETED");
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        // Remove all memberships for this tenant
        membershipService.removeAllByTenant(tenantId);

        log.info("tenant_deprovision_success tenantId={} status=DELETED", tenantId);
    }
}
