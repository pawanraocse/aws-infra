package com.learning.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "payment")
@Data
public class PaymentProperties {

    private String provider; // stripe, razorpay
    private StripeConfig stripe = new StripeConfig();
    private RazorpayConfig razorpay = new RazorpayConfig();
    private Map<String, TierConfig> tiers;

    @Data
    public static class StripeConfig {
        private String apiKey;
        private String webhookSecret;
        private String successUrl;
        private String cancelUrl;
        private String portalReturnUrl;
    }

    @Data
    public static class RazorpayConfig {
        private String keyId;
        private String keySecret;
        private String webhookSecret;
    }

    @Data
    public static class TierConfig {
        private String stripePriceId;
        private String razorpayPlanId;
    }
}
