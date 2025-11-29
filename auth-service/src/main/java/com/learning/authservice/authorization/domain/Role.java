package com.learning.authservice.authorization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Role entity for Permission-Based Access Control (PBAC).
 * Defines roles that can be assigned to users within tenants.
 */
@Entity
@Table(name = "roles", indexes = {
        @Index(name = "idx_roles_scope", columnList = "scope")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoleScope scope;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Role scope: PLATFORM (super-admin) or TENANT (tenant-specific roles)
     */
    public enum RoleScope {
        PLATFORM, // System-wide roles (e.g., super-admin)
        TENANT // Tenant-scoped roles (e.g., tenant-admin, tenant-user)
    }
}
