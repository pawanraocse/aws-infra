package com.learning.platformservice.membership.service;

import com.learning.platformservice.membership.dto.AddMembershipRequest;
import com.learning.platformservice.membership.dto.TenantLookupResponse;
import com.learning.platformservice.membership.entity.MembershipRoleHint;
import com.learning.platformservice.membership.entity.MembershipStatus;
import com.learning.platformservice.membership.entity.UserTenantMembership;
import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing user-tenant memberships.
 * 
 * <p>
 * Handles the multi-tenant login flow where one user can belong
 * to multiple tenants and select which one to access.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final UserTenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;

    private static final String DELETED_STATUS = "DELETED";
    private static final String DELETING_STATUS = "DELETING";
    private static final String SUSPENDED_STATUS = "SUSPENDED";

    /**
     * Find all active tenants for a user by email.
     * Used during login to populate the tenant selector.
     *
     * @param email User's email address
     * @return List of tenants the user can access, sorted by default then recent
     */
    @Transactional(readOnly = true)
    public List<TenantLookupResponse> findTenantsByEmail(String email) {
        log.debug("Looking up tenants for email: {}", maskEmail(email));

        List<UserTenantMembership> memberships = membershipRepository.findActiveByEmail(email);

        if (memberships.isEmpty()) {
            log.debug("No memberships found for email: {}", maskEmail(email));
            return List.of();
        }

        List<TenantLookupResponse> result = memberships.stream()
                .map(this::toTenantLookupResponse)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(TenantLookupResponse::isDefault).reversed()
                        .thenComparing(t -> t.getLastAccessedAt() != null
                                ? t.getLastAccessedAt()
                                : OffsetDateTime.MIN, Comparator.reverseOrder()))
                .toList();

        log.debug("Found {} active tenants for email: {}", result.size(), maskEmail(email));
        return result;
    }

    /**
     * Add a new membership for a user.
     *
     * @param request Membership details
     * @return The created membership
     * @throws IllegalStateException if membership already exists
     */
    @Transactional
    public UserTenantMembership addMembership(AddMembershipRequest request) {
        log.info("Adding membership: email={}, tenantId={}, roleHint={}",
                maskEmail(request.email()), request.tenantId(), request.roleHint());

        // Check if already exists
        if (membershipRepository.existsByEmailAndTenant(request.email(), request.tenantId())) {
            log.warn("Membership already exists: email={}, tenantId={}",
                    maskEmail(request.email()), request.tenantId());
            throw new IllegalStateException("Membership already exists for this user and tenant");
        }

        // Determine if this should be default (first membership for user)
        boolean shouldBeDefault = Boolean.TRUE.equals(request.isDefault())
                || membershipRepository.countActiveByEmail(request.email()) == 0;

        // If setting as default, clear existing defaults first
        if (shouldBeDefault) {
            membershipRepository.findDefaultByEmail(request.email())
                    .ifPresent(existing -> {
                        existing.clearDefault();
                        membershipRepository.saveAndFlush(existing); // Flush to ensure constraint is satisfied
                    });
        }

        UserTenantMembership membership = UserTenantMembership.builder()
                .userEmail(request.email().toLowerCase().trim())
                .cognitoUserId(request.cognitoUserId())
                .tenantId(request.tenantId())
                .roleHint(request.roleHint() != null
                        ? request.roleHint()
                        : MembershipRoleHint.MEMBER.getValue())
                .isOwner(Boolean.TRUE.equals(request.isOwner()))
                .isDefault(shouldBeDefault)
                .invitedBy(request.invitedBy())
                .joinedAt(OffsetDateTime.now())
                .status(MembershipStatus.ACTIVE)
                .build();

        UserTenantMembership saved = membershipRepository.save(membership);
        log.info("Created membership: id={}, email={}, tenantId={}",
                saved.getId(), maskEmail(request.email()), request.tenantId());

        return saved;
    }

    /**
     * Update the last accessed timestamp for a membership.
     * Called after successful login.
     */
    @Transactional
    public void updateLastAccessed(String email, String tenantId) {
        membershipRepository.findByEmailAndTenant(email, tenantId)
                .ifPresent(m -> {
                    m.touchLastAccessed();
                    membershipRepository.save(m);
                    log.debug("Updated last accessed for email={}, tenantId={}",
                            maskEmail(email), tenantId);
                });
    }

    /**
     * Check if a user can create a personal tenant.
     * Rule: Only 1 personal tenant per email.
     */
    @Transactional(readOnly = true)
    public boolean canCreatePersonalTenant(String email) {
        return !membershipRepository.hasPersonalTenant(email);
    }

    /**
     * Find membership by email and tenant.
     */
    @Transactional(readOnly = true)
    public Optional<UserTenantMembership> findByEmailAndTenant(String email, String tenantId) {
        return membershipRepository.findByEmailAndTenant(email, tenantId);
    }

    /**
     * Update Cognito user ID for all memberships of an email.
     * Called after first login when we have the Cognito ID.
     */
    @Transactional
    public void updateCognitoId(String email, String cognitoUserId) {
        int updated = membershipRepository.updateCognitoIdByEmail(email, cognitoUserId);
        log.debug("Updated Cognito ID for {} memberships of email={}", updated, maskEmail(email));
    }

    /**
     * Count active memberships for a user.
     * Used to determine if Cognito user should be deleted when an account is
     * removed.
     *
     * @param email User's email
     * @return Count of active memberships (ACTIVE status only)
     */
    @Transactional(readOnly = true)
    public long countActiveByEmail(String email) {
        long count = membershipRepository.countActiveByEmail(email);
        log.debug("Found {} active memberships for email={}", count, maskEmail(email));
        return count;
    }

    /**
     * Remove all memberships for a tenant (when tenant is deleted).
     */
    @Transactional
    public void removeAllByTenant(String tenantId) {
        int removed = membershipRepository.removeAllByTenant(tenantId);
        log.info("Removed {} memberships for deleted tenant: {}", removed, tenantId);
    }

    // ========== Private Helpers ==========

    private TenantLookupResponse toTenantLookupResponse(UserTenantMembership membership) {
        Tenant tenant = tenantRepository.findById(membership.getTenantId()).orElse(null);

        // Filter out tenants that are not accessible
        if (tenant == null || !isTenantAccessible(tenant)) {
            return null;
        }

        return TenantLookupResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .tenantType(tenant.getTenantType().name())
                .companyName(tenant.getCompanyName())
                .logoUrl(tenant.getLogoUrl())
                .ssoEnabled(Boolean.TRUE.equals(tenant.getSsoEnabled()))
                .ssoProvider(tenant.getIdpType() != null ? tenant.getIdpType().name() : null)
                .roleHint(membership.getRoleHint())
                .isOwner(Boolean.TRUE.equals(membership.getIsOwner()))
                .isDefault(Boolean.TRUE.equals(membership.getIsDefault()))
                .lastAccessedAt(membership.getLastAccessedAt())
                .build();
    }

    /**
     * Check if a tenant is accessible for login.
     * Filters out tenants that are deleted, being deleted, or suspended.
     */
    private boolean isTenantAccessible(Tenant tenant) {
        String status = tenant.getStatus();
        return !DELETED_STATUS.equals(status)
                && !DELETING_STATUS.equals(status)
                && !SUSPENDED_STATUS.equals(status);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
