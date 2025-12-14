package com.learning.platformservice.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Audit record for deleted accounts.
 * Used to track deletions and detect re-registration attempts.
 */
@Entity
@Table(name = "deleted_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "original_tenant_id", nullable = false)
    private String originalTenantId;

    @Column(name = "tenant_type", nullable = false)
    private String tenantType;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "deleted_at", nullable = false)
    @Builder.Default
    private OffsetDateTime deletedAt = OffsetDateTime.now();

    @Column(name = "deleted_by")
    private String deletedBy;
}
