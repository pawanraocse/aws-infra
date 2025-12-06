package com.learning.platformservice.tenant.entity;

import com.learning.common.dto.TenantType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant {
    // ========== Existing Core Fields (DO NOT REMOVE) ==========
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "storage_mode", nullable = false)
    private String storageMode;

    @Column(name = "jdbc_url")
    private String jdbcUrl;

    @Column(name = "db_user_secret_ref")
    private String dbUserSecretRef;

    @Column(name = "db_user_password_enc")
    private String dbUserPasswordEnc;

    @Column(name = "sla_tier", nullable = false)
    private String slaTier;

    @Column(name = "last_migration_version")
    private String lastMigrationVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ========== NEW: Multi-Tenancy Type Fields ==========
    @Column(name = "tenant_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantType tenantType = TenantType.PERSONAL;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 1;

    // ========== NEW: Organization Profile Fields ==========
    @Column(name = "company_name")
    private String companyName;

    @Column(name = "industry")
    private String industry;

    @Column(name = "company_size")
    private String companySize;

    @Column(name = "website")
    private String website;

    @Column(name = "logo_url")
    private String logoUrl;

    // ========== NEW: SSO/IDP Configuration ==========
    @Column(name = "sso_enabled")
    private Boolean ssoEnabled = false;

    @Column(name = "idp_type")
    @Enumerated(EnumType.STRING)
    private IdpType idpType;

    @Column(name = "idp_metadata_url")
    private String idpMetadataUrl;

    @Column(name = "idp_entity_id")
    private String idpEntityId;

    @Column(name = "idp_config_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> idpConfigJson;

    // ========== NEW: Security & Compliance ==========
    @Column(name = "encryption_key_id")
    private String encryptionKeyId;

    @Column(name = "data_residency")
    private String dataResidency;

    // ========== NEW: Performance & Scalability ==========
    @Column(name = "db_shard")
    private String dbShard = "shard-1";

    @Column(name = "read_replica_url")
    private String readReplicaUrl;

    @Column(name = "connection_pool_min")
    private Integer connectionPoolMin = 2;

    @Column(name = "connection_pool_max")
    private Integer connectionPoolMax = 10;

    // ========== NEW: Lifecycle Management ==========
    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(name = "subscription_status")
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "archived_to_s3")
    private Boolean archivedToS3 = false;
}
