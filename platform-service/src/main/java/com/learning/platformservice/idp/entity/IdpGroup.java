package com.learning.platformservice.idp.entity;

import com.learning.platformservice.tenant.entity.IdpType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a group synced from an external Identity Provider.
 * 
 * When users login via SSO (SAML/OIDC), their group memberships are extracted
 * from the assertion/token and stored here for role mapping.
 */
@Entity
@Table(name = "idp_groups", indexes = {
        @Index(name = "idx_idpg_tenant", columnList = "tenant_id"),
        @Index(name = "idx_idpg_external_id", columnList = "external_group_id"),
        @Index(name = "idx_idpg_type", columnList = "idp_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_idpg_tenant_external", columnNames = { "tenant_id", "external_group_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdpGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tenant this group belongs to.
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * Original group identifier from the IdP (e.g., SAML DN or OIDC group name).
     * Example: "cn=Engineering,ou=Groups,dc=company,dc=com" or "engineering-team"
     */
    @Column(name = "external_group_id", nullable = false, length = 512)
    private String externalGroupId;

    /**
     * Human-readable display name for the group.
     */
    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    /**
     * Type of Identity Provider this group was synced from.
     */
    @Column(name = "idp_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private IdpType idpType;

    /**
     * Approximate member count (if available from IdP).
     */
    @Column(name = "member_count")
    private Integer memberCount;

    /**
     * Last time this group was seen in an SSO login.
     */
    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
