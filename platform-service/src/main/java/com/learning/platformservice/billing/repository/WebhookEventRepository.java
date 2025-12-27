package com.learning.platformservice.billing.repository;

import com.learning.platformservice.billing.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    boolean existsByStripeEventId(String stripeEventId);
}
