package com.learning.platformservice.billing.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Stripe billing integration.
 * All pricing and tier information is externalized for easy configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "billing")
@Getter
@Setter
public class BillingProperties {

    /**
     * Master toggle for billing functionality.
     */
    private boolean enabled = true;

    /**
     * B2C-specific billing settings.
     */
    private B2cSettings b2c = new B2cSettings();

    /**
     * Stripe-specific configuration.
     */
    private StripeSettings stripe = new StripeSettings();

    /**
     * Pricing tier definitions.
     */
    private Map<String, TierConfig> tiers = new HashMap<>();

    @PostConstruct
    public void init() {
        if (enabled && stripe.getApiKey() != null && !stripe.getApiKey().isBlank()) {
            Stripe.apiKey = stripe.getApiKey();
        }
    }

    @Getter
    @Setter
    public static class B2cSettings {
        /**
         * Enable billing for B2C (personal) accounts.
         * Default: false (B2C is free tier)
         */
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class StripeSettings {
        private String apiKey;
        private String webhookSecret;
        private String successUrl = "http://localhost:4200/settings/billing?success=true";
        private String cancelUrl = "http://localhost:4200/settings/billing?canceled=true";
    }

    @Getter
    @Setter
    public static class TierConfig {
        private String priceId;
        private String name;
        private int maxUsers = -1; // -1 = unlimited
    }
}
