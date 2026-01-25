package com.learning.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "billing_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccount {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId; // Maps to tenantId

    @Column(name = "email")
    private String email;

    @Column(name = "provider", length = 20)
    private String provider; // STRIPE, RAZORPAY

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "status", length = 32)
    private String status; // ACTIVE, PAST_DUE, CANCELED, TRIALING

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
