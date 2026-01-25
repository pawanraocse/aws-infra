package com.learning.payment.service;

import com.learning.payment.dto.CheckoutResponse;

import java.util.Map;

public interface PaymentProvider {
    
    /**
     * Create a checkout session for a subscription.
     */
    CheckoutResponse createCheckoutSession(String accountId, String email, String tierKey, String successUrl, String cancelUrl);

    /**
     * Create a self-service billing portal session.
     */
    String createPortalSession(String accountId, String returnUrl);

    /**
     * Handle webhook payload from the provider.
     * @return true if handled successfully
     */
    boolean handleWebhook(String payload, Map<String, String> headers);
    
    /**
     * Get the provider name (STRIPE, RAZORPAY).
     */
    String getProviderName();
}
