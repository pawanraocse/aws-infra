package com.learning.authservice.authorization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * User role assignment within a specific tenant.
 * Maps users (identified by Cognito user ID) to roles within tenants.
 */
@Entity
@Table(name = "user_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_roles", columnNames = { "user_id", "tenant_id", "role_id" })
}, indexes = {
        @Index(name = "idx_user_roles_lookup", columnList = "user_id,tenant_id"),
        @Index(name = "idx_user_roles_tenant", columnList = "tenant_id"),
        @Index(name = "idx_user_roles_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cognito user ID (sub claim from JWT)
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * Tenant ID for tenant-scoped roles
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * Role ID (foreign key to roles table)
     */
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;

    /**
     * User ID who assigned this role (for audit trail)
     */
    @Column(name = "assigned_by")
    private String assignedBy;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    /**
     * Optional expiration timestamp for temporary role assignments
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Check if this role assignment has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this role assignment is active (not expired)
     */
    public boolean isActive() {
        return !isExpired();
    }
}
