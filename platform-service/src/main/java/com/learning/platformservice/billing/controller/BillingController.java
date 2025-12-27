package com.learning.platformservice.billing.controller;

import com.learning.platformservice.billing.config.BillingProperties;
import com.learning.platformservice.billing.dto.CheckoutRequest;
import com.learning.platformservice.billing.dto.CheckoutResponse;
import com.learning.platformservice.billing.dto.PortalResponse;
import com.learning.platformservice.billing.dto.SubscriptionStatusDTO;
import com.learning.platformservice.billing.service.StripeService;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Stripe checkout and billing portal operations.
 * Requires authenticated requests (Gateway passes X-Tenant-Id and X-Email
 * headers).
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingProperties billingProperties;
    private final StripeService stripeService;
    private final TenantRepository tenantRepository;

    /**
     * Create a Stripe Checkout session for the specified tier.
     */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Email") String email,
            @Valid @RequestBody CheckoutRequest request) throws StripeException {
        if (!billingProperties.isEnabled()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Creating checkout session for tenant={}, tier={}", tenantId, request.getTier());

        com.stripe.model.checkout.Session session = stripeService.createCheckoutSession(
                tenantId,
                email,
                request.getTier());

        return ResponseEntity.ok(new CheckoutResponse(session.getUrl(), session.getId()));
    }

    /**
     * Create a Stripe Customer Portal session for self-service billing management.
     */
    @PostMapping("/portal")
    public ResponseEntity<PortalResponse> createPortal(
            @RequestHeader("X-Tenant-Id") String tenantId) throws StripeException {
        if (!billingProperties.isEnabled()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Creating portal session for tenant={}", tenantId);

        Session session = stripeService.createPortalSession(tenantId);

        return ResponseEntity.ok(new PortalResponse(session.getUrl()));
    }

    /**
     * Get current subscription status for the tenant.
     */
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusDTO> getStatus(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        boolean hasActive = tenant.getSubscriptionStatus() != null &&
                (tenant.getSubscriptionStatus().name().equals("ACTIVE") ||
                        tenant.getSubscriptionStatus().name().equals("TRIAL"));

        // Map price ID back to tier name
        String tierName = mapPriceIdToTierName(tenant.getStripePriceId());

        SubscriptionStatusDTO status = SubscriptionStatusDTO.builder()
                .status(tenant.getSubscriptionStatus() != null ? tenant.getSubscriptionStatus().name() : "NONE")
                .tier(tierName)
                .currentPeriodEnd(tenant.getCurrentPeriodEnd())
                .trialEndsAt(tenant.getTrialEndsAt())
                .hasActiveSubscription(hasActive)
                .build();

        return ResponseEntity.ok(status);
    }

    /**
     * Map Stripe price ID back to tier name (starter, pro, enterprise).
     */
    private String mapPriceIdToTierName(String priceId) {
        if (priceId == null)
            return null;

        for (var entry : billingProperties.getTiers().entrySet()) {
            if (priceId.equals(entry.getValue().getPriceId())) {
                return entry.getKey();
            }
        }
        return null; // Unknown price ID
    }
}
