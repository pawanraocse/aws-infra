package com.learning.authservice.authorization.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a mapping from an external IdP group to an application
 * role.
 * 
 * When users login via SSO, their IdP groups are matched against this table
 * to automatically assign roles.
 */
@Entity
@Table(name = "group_role_mappings", indexes = {
        @Index(name = "idx_grm_role", columnList = "role_id"),
        @Index(name = "idx_grm_group", columnList = "external_group_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_grm_external_group", columnNames = { "external_group_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * External group identifier from IdP.
     * This must match the group ID stored in idp_groups table.
     */
    @Column(name = "external_group_id", nullable = false, length = 512)
    private String externalGroupId;

    /**
     * Human-readable display name for the group.
     */
    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    /**
     * Target role to assign when user is in this group.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Priority for conflict resolution.
     * When a user belongs to multiple groups, the role from the highest priority
     * mapping wins.
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Whether to automatically assign this role on SSO login.
     * If false, the mapping exists but requires manual approval.
     */
    @Column(name = "auto_assign")
    @Builder.Default
    private Boolean autoAssign = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * ID of the admin user who created this mapping.
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;
}
