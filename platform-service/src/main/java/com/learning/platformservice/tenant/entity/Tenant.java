package com.learning.platformservice.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant {
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
}
