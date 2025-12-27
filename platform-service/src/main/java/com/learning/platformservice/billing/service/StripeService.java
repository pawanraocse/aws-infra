package com.learning.platformservice.billing.service;

import com.learning.platformservice.billing.config.BillingProperties;
import com.learning.platformservice.billing.entity.StripeCustomer;
import com.learning.platformservice.billing.repository.StripeCustomerRepository;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.billingportal.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Wrapper service for Stripe SDK operations.
 * Handles customer creation, checkout sessions, and portal sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final BillingProperties billingProperties;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final TenantRepository tenantRepository;

    /**
     * Get or create a Stripe customer for the given tenant.
     */
    @Transactional
    public StripeCustomer getOrCreateCustomer(String tenantId, String email) throws StripeException {
        // Check if customer already exists
        Optional<StripeCustomer> existing = stripeCustomerRepository.findByTenantId(tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Get tenant for metadata
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Create Stripe customer
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(tenant.getName())
                .putMetadata("tenant_id", tenantId)
                .putMetadata("tenant_type", tenant.getTenantType().name())
                .build();

        Customer stripeCustomer = Customer.create(params);

        // Save mapping
        StripeCustomer customer = new StripeCustomer(tenantId, stripeCustomer.getId(), email);
        return stripeCustomerRepository.save(customer);
    }

    /**
     * Create a Stripe Checkout session for subscription.
     */
    public com.stripe.model.checkout.Session createCheckoutSession(
            String tenantId,
            String email,
            String tierKey) throws StripeException {
        // Get or create customer
        StripeCustomer customer = getOrCreateCustomer(tenantId, email);

        // Get tier config
        BillingProperties.TierConfig tierConfig = billingProperties.getTiers().get(tierKey);
        if (tierConfig == null) {
            throw new IllegalArgumentException("Unknown tier: " + tierKey);
        }

        // Create checkout session
        com.stripe.param.checkout.SessionCreateParams params = com.stripe.param.checkout.SessionCreateParams.builder()
                .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customer.getStripeCustomerId())
                .setSuccessUrl(billingProperties.getStripe().getSuccessUrl())
                .setCancelUrl(billingProperties.getStripe().getCancelUrl())
                .addLineItem(
                        com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                .setPrice(tierConfig.getPriceId())
                                .setQuantity(1L)
                                .build())
                .putMetadata("tenant_id", tenantId)
                .putMetadata("tier", tierKey)
                .build();

        return com.stripe.model.checkout.Session.create(params);
    }

    /**
     * Create a Stripe Customer Portal session for self-service billing management.
     */
    public Session createPortalSession(String tenantId) throws StripeException {
        StripeCustomer customer = stripeCustomerRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No billing record for tenant: " + tenantId));

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(customer.getStripeCustomerId())
                .setReturnUrl(billingProperties.getStripe().getSuccessUrl())
                .build();

        return Session.create(params);
    }
}
