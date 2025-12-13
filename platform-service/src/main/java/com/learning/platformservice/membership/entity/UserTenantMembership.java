package com.learning.platformservice.membership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a user's membership in a tenant.
 * 
 * <p>
 * This enables multi-tenant login where one user (identified by email)
 * can belong to multiple tenants and select which one to access during login.
 * </p>
 * 
 * <h3>Rules:</h3>
 * <ul>
 * <li>PERSONAL tenant: Max 1 per email (enforced at application level)</li>
 * <li>ORGANIZATION tenant: Unlimited per email</li>
 * <li>A user can have both personal + organization memberships</li>
 * </ul>
 */
@Entity
@Table(name = "user_tenant_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTenantMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User's email address - primary identifier for lookup.
     */
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /**
     * Cognito user ID - set after first login for faster lookups.
     */
    @Column(name = "cognito_user_id")
    private String cognitoUserId;

    /**
     * Reference to the tenant this membership is for.
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * Role hint for display in tenant selector UI.
     * Actual permissions are managed in the tenant database.
     */
    @Column(name = "role_hint", nullable = false)
    @Builder.Default
    private String roleHint = MembershipRoleHint.MEMBER.getValue();

    /**
     * True if this user created/owns the tenant.
     * Personal tenant owner or organization founder.
     */
    @Column(name = "is_owner", nullable = false)
    @Builder.Default
    private Boolean isOwner = false;

    /**
     * True if this is the user's default workspace.
     * Only one membership per user can be default (enforced by partial unique
     * index).
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Timestamp of last access to this tenant.
     * Used for sorting (most recently used first).
     */
    @Column(name = "last_accessed_at")
    private OffsetDateTime lastAccessedAt;

    /**
     * Timestamp when user joined this tenant.
     */
    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    /**
     * Email of the user who invited this member.
     * Null for owners/self-signup.
     */
    @Column(name = "invited_by")
    private String invitedBy;

    /**
     * Membership status for soft delete support.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    /**
     * Marks this membership as the user's default workspace.
     */
    public void makeDefault() {
        this.isDefault = true;
    }

    /**
     * Removes default status from this membership.
     */
    public void clearDefault() {
        this.isDefault = false;
    }

    /**
     * Updates the last accessed timestamp to now.
     */
    public void touchLastAccessed() {
        this.lastAccessedAt = OffsetDateTime.now();
    }

    /**
     * Soft-deletes this membership.
     */
    public void remove() {
        this.status = MembershipStatus.REMOVED;
        this.isDefault = false;
    }

    /**
     * Suspends this membership.
     */
    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }

    /**
     * Reactivates a removed or suspended membership.
     */
    public void reactivate() {
        this.status = MembershipStatus.ACTIVE;
    }

    /**
     * Checks if this membership is active.
     */
    public boolean isActive() {
        return this.status == MembershipStatus.ACTIVE;
    }
}
