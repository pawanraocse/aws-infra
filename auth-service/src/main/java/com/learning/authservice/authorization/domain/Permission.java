package com.learning.authservice.authorization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Permission entity for fine-grained access control.
 * Format: resource:action (e.g., "entry:create", "user:manage")
 */
@Entity
@Table(name = "permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_permissions_resource_action", columnNames = { "resource", "action" })
}, indexes = {
        @Index(name = "idx_permissions_resource", columnList = "resource")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 50)
    private String resource;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Returns the full permission string in format "resource:action"
     */
    public String getFullPermission() {
        return resource + ":" + action;
    }
}
