package com.learning.authservice.authorization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * ACL Entry for resource-level access control.
 * Enables fine-grained, per-resource permissions (Google Drive style sharing).
 */
@Entity
@Table(name = "acl_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType; // PROJECT, FOLDER, FILE, ASSET

    @Column(name = "principal_type", nullable = false, length = 32)
    private String principalType; // USER, GROUP, PUBLIC

    @Column(name = "principal_id", length = 255)
    private String principalId; // User ID, Group ID, or null for PUBLIC

    @Column(name = "role_bundle", nullable = false, length = 32)
    private String roleBundle; // VIEWER, CONTRIBUTOR, EDITOR, MANAGER

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> permissions; // Expanded permissions list

    @Column(name = "granted_by", length = 255)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;
}
