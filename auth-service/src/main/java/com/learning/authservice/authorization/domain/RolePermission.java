package com.learning.authservice.authorization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Many-to-Many relationship between Roles and Permissions.
 * Maps which permissions are granted to which roles.
 */
@Entity
@Table(name = "role_permissions", indexes = {
        @Index(name = "idx_role_permissions_role", columnList = "role_id"),
        @Index(name = "idx_role_permissions_permission", columnList = "permission_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(RolePermission.RolePermissionId.class)
public class RolePermission {

    @Id
    @Column(name = "role_id", length = 64)
    private String roleId;

    @Id
    @Column(name = "permission_id", length = 64)
    private String permissionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Composite key class
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RolePermissionId implements Serializable {
        private String roleId;
        private String permissionId;
    }
}
