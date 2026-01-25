package com.learning.payment.service.provider;

import com.learning.payment.config.PaymentProperties;
import com.learning.payment.dto.CheckoutResponse;
import com.learning.payment.service.PaymentProvider;
import com.razorpay.RazorpayClient;
import com.razorpay.Subscription;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayProvider implements PaymentProvider {

    private final PaymentProperties properties;

    @Override
    public String getProviderName() {
        return "razorpay";
    }

    @Override
    public CheckoutResponse createCheckoutSession(String accountId, String email, String tierKey, String successUrl,
            String cancelUrl) {
        log.info("Creating Razorpay checkout session for account: {} tier: {}", accountId, tierKey);
        try {
            RazorpayClient client = new RazorpayClient(
                    properties.getRazorpay().getKeyId(),
                    properties.getRazorpay().getKeySecret());

            // 1. Create Subscription
            String planId = properties.getTiers().get(tierKey).getRazorpayPlanId();
            log.debug("Using Razorpay Plan ID: {}", planId);

            JSONObject subRequest = new JSONObject();
            subRequest.put("plan_id", planId);
            subRequest.put("total_count", 120); // 10 years
            subRequest.put("quantity", 1);
            subRequest.put("notes", new JSONObject().put("account_id", accountId));

            Subscription subscription = client.subscriptions.create(subRequest);
            String subId = subscription.get("id");

            log.info("Razorpay subscription created: {}", subId);

            // Razorpay uses client-side checkout mostly, but we can return the subId
            // for the frontend to initialize the standard checkout.
            // Or construct a payment link.
            // For standard subscription, we return the subId as the "sessionId" equivalent
            return new CheckoutResponse(null, subId, properties.getRazorpay().getKeyId(), "razorpay");

        } catch (Exception e) {
            log.error("Failed to create Razorpay checkout session", e);
            throw new RuntimeException("Razorpay error: " + e.getMessage(), e);
        }
    }

    @Override
    public String createPortalSession(String accountId, String returnUrl) {
        throw new UnsupportedOperationException("Razorpay does not have a native hosted portal like Stripe.");
    }

    @Override
    public boolean handleWebhook(String payload, Map<String, String> headers) {
        String signature = headers.get("x-razorpay-signature");
        if (signature == null)
            return false;

        try {
            // Verify signature
            Utils.verifyWebhookSignature(payload, signature, properties.getRazorpay().getWebhookSecret());

            JSONObject json = new JSONObject(payload);
            String event = json.getString("event");

            // Handle subscription.charged, subscription.activated
            log.info("Handled Razorpay event: {}", event);
            return true;
        } catch (Exception e) {
            log.error("Razorpay webhook error", e);
            return false;
        }
    }
}
