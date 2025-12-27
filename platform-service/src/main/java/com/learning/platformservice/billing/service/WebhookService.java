package com.learning.platformservice.billing.service;

import com.learning.platformservice.billing.config.BillingProperties;
import com.learning.platformservice.billing.entity.WebhookEvent;
import com.learning.platformservice.billing.repository.StripeCustomerRepository;
import com.learning.platformservice.billing.repository.WebhookEventRepository;
import com.learning.platformservice.tenant.entity.SubscriptionStatus;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Service for processing Stripe webhook events.
 * Implements idempotency to prevent duplicate processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final TenantRepository tenantRepository;
    private final BillingProperties billingProperties;

    /**
     * Process a Stripe webhook event.
     * Returns true if the event was processed, false if it was a duplicate.
     */
    @Transactional
    public boolean processEvent(Event event) {
        String eventId = event.getId();
        String eventType = event.getType();

        // Idempotency check
        if (webhookEventRepository.existsByStripeEventId(eventId)) {
            log.info("Duplicate webhook event ignored: {}", eventId);
            return false;
        }

        // Record event
        WebhookEvent webhookEvent = new WebhookEvent(eventId, eventType);
        webhookEventRepository.save(webhookEvent);

        try {
            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutCompleted(event);
                    break;
                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event);
                    break;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;
                case "invoice.paid":
                    handleInvoicePaid(event);
                    break;
                case "invoice.payment_failed":
                    handlePaymentFailed(event);
                    break;
                default:
                    log.info("Unhandled webhook event type: {}", eventType);
            }
            webhookEvent.markProcessed();
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", eventId, e);
            webhookEvent.markFailed(e.getMessage());
            throw e;
        }

        webhookEventRepository.save(webhookEvent);
        return true;
    }

    private void handleCheckoutCompleted(Event event) {
        log.info("WEBHOOK_DEBUG: Starting handleCheckoutCompleted for event: {}", event.getId());

        try {
            Session session = deserializeEvent(event, Session.class);
            if (session == null) {
                log.error("WEBHOOK_DEBUG: Failed to deserialize checkout session");
                return;
            }

            log.info("WEBHOOK_DEBUG: Session ID: {}, Customer: {}", session.getId(), session.getCustomer());

            // Get metadata with null-safety
            var metadata = session.getMetadata();
            log.info("WEBHOOK_DEBUG: Metadata: {}", metadata);

            if (metadata == null || metadata.isEmpty()) {
                log.warn("WEBHOOK_DEBUG: Checkout session has no metadata - cannot update tenant");
                return;
            }

            String tenantId = metadata.get("tenant_id");
            String tier = metadata.get("tier");
            String subscriptionId = session.getSubscription();
            log.info("WEBHOOK_DEBUG: tenantId={}, tier={}, subscriptionId={}", tenantId, tier, subscriptionId);

            if (tenantId == null) {
                log.warn("WEBHOOK_DEBUG: Checkout session missing tenant_id in metadata");
                return;
            }

            log.info("Checkout completed for tenant={}, tier={}, subscription={}", tenantId, tier, subscriptionId);

            // Update tenant
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
            log.info("WEBHOOK_DEBUG: Found tenant: {}", tenant.getName());

            BillingProperties.TierConfig tierConfig = tier != null ? billingProperties.getTiers().get(tier) : null;
            log.info("WEBHOOK_DEBUG: Tier config: {}", tierConfig);

            tenant.setStripeSubscriptionId(subscriptionId);
            tenant.setStripePriceId(tierConfig != null ? tierConfig.getPriceId() : null);
            tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);

            // Update max users based on tier
            if (tierConfig != null && tierConfig.getMaxUsers() > 0) {
                tenant.setMaxUsers(tierConfig.getMaxUsers());
            }

            // Fetch subscription to get period end
            if (subscriptionId != null) {
                try {
                    Subscription stripeSubscription = Subscription.retrieve(subscriptionId);
                    tenant.setCurrentPeriodEnd(toOffsetDateTime(stripeSubscription.getCurrentPeriodEnd()));
                    log.info("WEBHOOK_DEBUG: Set currentPeriodEnd to {}", tenant.getCurrentPeriodEnd());
                } catch (Exception e) {
                    log.warn("Failed to fetch subscription period end: {}", e.getMessage());
                }
            }

            tenantRepository.save(tenant);
            log.info("WEBHOOK_DEBUG: SUCCESS - Updated tenant {} to ACTIVE with subscription {}", tenantId,
                    subscriptionId);

        } catch (Exception e) {
            log.error("WEBHOOK_DEBUG: Exception in handleCheckoutCompleted: {} - {}", e.getClass().getName(),
                    e.getMessage(), e);
            throw e;
        }
    }

    private void handleSubscriptionUpdated(Event event) {
        Subscription subscription = deserializeEvent(event, Subscription.class);
        if (subscription == null)
            return;

        String subscriptionId = subscription.getId();
        String status = subscription.getStatus();
        Long periodEnd = subscription.getCurrentPeriodEnd();

        log.info("Subscription updated: id={}, status={}", subscriptionId, status);

        tenantRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(tenant -> {
            tenant.setSubscriptionStatus(mapStripeStatus(status));
            tenant.setCurrentPeriodEnd(toOffsetDateTime(periodEnd));
            tenantRepository.save(tenant);
        });
    }

    private void handleSubscriptionDeleted(Event event) {
        Subscription subscription = deserializeEvent(event, Subscription.class);
        if (subscription == null)
            return;

        String subscriptionId = subscription.getId();

        log.info("Subscription deleted: id={}", subscriptionId);

        tenantRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(tenant -> {
            tenant.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
            tenantRepository.save(tenant);
        });
    }

    private void handleInvoicePaid(Event event) {
        Invoice invoice = deserializeEvent(event, Invoice.class);
        if (invoice == null)
            return;

        log.info("Invoice paid: customer={}, amount={}", invoice.getCustomer(), invoice.getAmountPaid());
        // Could store payment history here if needed
    }

    private void handlePaymentFailed(Event event) {
        Invoice invoice = deserializeEvent(event, Invoice.class);
        if (invoice == null)
            return;

        String subscriptionId = invoice.getSubscription();

        log.warn("Payment failed for subscription: {}", subscriptionId);

        tenantRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(tenant -> {
            tenant.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
            tenantRepository.save(tenant);
        });
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELLED;
            case "trialing" -> SubscriptionStatus.TRIAL;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private OffsetDateTime toOffsetDateTime(Long epochSeconds) {
        if (epochSeconds == null)
            return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    /**
     * Safely deserialize Stripe event data, handling API version mismatches.
     * Uses deserializeUnsafe() when the SDK version doesn't match the webhook API
     * version.
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeEvent(Event event, Class<T> clazz) {
        try {
            var deserializer = event.getDataObjectDeserializer();
            if (deserializer.getObject().isPresent()) {
                return (T) deserializer.getObject().get();
            }
            // API version mismatch - use unsafe deserialization
            log.debug("Using deserializeUnsafe() for event {} due to API version mismatch", event.getId());
            return (T) deserializer.deserializeUnsafe();
        } catch (Exception e) {
            log.error("Failed to deserialize event {}: {}", event.getId(), e.getMessage());
            return null;
        }
    }
}
