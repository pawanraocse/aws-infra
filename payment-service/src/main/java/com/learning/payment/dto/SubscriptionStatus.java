package com.learning.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatus {
    private String status;
    private String tier;
    private Instant currentPeriodEnd;
    private Instant trialEndsAt;
    private boolean hasActiveSubscription;
}
