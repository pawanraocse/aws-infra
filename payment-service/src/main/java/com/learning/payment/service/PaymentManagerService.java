package com.learning.payment.service;

import com.learning.payment.config.PaymentProperties;
import com.learning.payment.dto.CheckoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentManagerService {

    private final java.util.List<PaymentProvider> providers;
    private final PaymentProperties properties;
    private final com.learning.payment.repository.BillingAccountRepository repository;

    private PaymentProvider getActiveProvider() {
        String activeName = properties.getProvider(); // "stripe" or "razorpay"
        return providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(activeName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + activeName));
    }

    public com.learning.payment.dto.SubscriptionStatus getSubscriptionStatus(String accountId) {
        return repository.findById(accountId)
                .map(account -> com.learning.payment.dto.SubscriptionStatus.builder()
                        .status(account.getStatus())
                        .tier(account.getPlanId())
                        .currentPeriodEnd(account.getCurrentPeriodEnd())
                        // Logic for trial ends at if not in DB?
                        // For now we map what we have.
                        .hasActiveSubscription("ACTIVE".equalsIgnoreCase(account.getStatus())
                                || "TRIALING".equalsIgnoreCase(account.getStatus()))
                        .build())
                .orElse(com.learning.payment.dto.SubscriptionStatus.builder()
                        .status("NO_ACCOUNT")
                        .hasActiveSubscription(false)
                        .build());
    }

    public CheckoutResponse createCheckout(String accountId, String email, String tier, String successUrl,
            String cancelUrl) {
        return getActiveProvider().createCheckoutSession(accountId, email, tier, successUrl, cancelUrl);
    }

    public String createPortal(String accountId, String returnUrl) {
        return getActiveProvider().createPortalSession(accountId, returnUrl);
    }

    public void handleWebhook(String providerName, String payload, Map<String, String> headers) {
        PaymentProvider provider = providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider webhook: " + providerName));

        provider.handleWebhook(payload, headers);
    }
}
