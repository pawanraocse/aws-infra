package com.learning.platformservice.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class SubscriptionStatusDTO {
    private String status;
    private String tier;
    private OffsetDateTime currentPeriodEnd;
    private OffsetDateTime trialEndsAt;
    private boolean hasActiveSubscription;
}
