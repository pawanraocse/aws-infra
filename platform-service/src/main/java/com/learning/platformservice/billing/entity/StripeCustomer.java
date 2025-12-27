package com.learning.platformservice.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps a tenant to their Stripe customer record.
 * Stored in Platform DB (shared), not tenant-specific DB.
 */
@Entity
@Table(name = "stripe_customers")
@Getter
@Setter
@NoArgsConstructor
public class StripeCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Column(name = "stripe_customer_id", nullable = false, unique = true)
    private String stripeCustomerId;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public StripeCustomer(String tenantId, String stripeCustomerId, String email) {
        this.tenantId = tenantId;
        this.stripeCustomerId = stripeCustomerId;
        this.email = email;
    }
}
