package com.learning.platformservice.tenant.entity;

/**
 * Subscription status enumeration for tenant lifecycle management.
 */
public enum SubscriptionStatus {
    TRIAL, // Trial period active
    ACTIVE, // Paid and active subscription
    PAST_DUE, // Payment failed, grace period
    SUSPENDED, // Temporarily suspended (payment issue, violation, etc.)
    CANCELLED // Subscription cancelled
}
