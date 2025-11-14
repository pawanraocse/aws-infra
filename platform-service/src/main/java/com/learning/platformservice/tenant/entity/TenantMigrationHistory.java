package com.learning.platformservice.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_migration_history")
@Getter
@Setter
public class TenantMigrationHistory {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String version;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "ended_at")
    private OffsetDateTime endedAt;
    @Column(nullable = false)
    private String status; // SUCCESS / FAILED
    @Column
    private String notes;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }
}

