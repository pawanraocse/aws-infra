package com.learning.payment.service.provider;

import com.learning.payment.config.PaymentProperties;
import com.learning.payment.dto.CheckoutResponse;
import com.learning.payment.entity.BillingAccount;
import com.learning.payment.repository.BillingAccountRepository;
import com.learning.payment.service.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeProvider implements PaymentProvider, InitializingBean {

    private final PaymentProperties properties;
    private final BillingAccountRepository repository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void afterPropertiesSet() {
        if (properties.getStripe() != null && properties.getStripe().getApiKey() != null) {
            Stripe.apiKey = properties.getStripe().getApiKey();
        }
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }

    @Override
    public CheckoutResponse createCheckoutSession(String accountId, String email, String tierKey, String successUrl,
            String cancelUrl) {
        try {
            Customer customer = getOrCreateCustomer(accountId, email);
            String priceId = getPriceId(tierKey);

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customer.getId())
                    .setSuccessUrl(successUrl != null ? successUrl : properties.getStripe().getSuccessUrl())
                    .setCancelUrl(cancelUrl != null ? cancelUrl : properties.getStripe().getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPrice(priceId)
                            .build())
                    .putMetadata("account_id", accountId)
                    .putMetadata("tier", tierKey)
                    .build();

            Session session = Session.create(params);
            return new CheckoutResponse(session.getUrl(), session.getId(), null, "stripe");

        } catch (Exception e) {
            log.error("Stripe checkout error", e);
            throw new RuntimeException("Failed to create Stripe checkout session", e);
        }
    }

    @Override
    public String createPortalSession(String accountId, String returnUrl) {
        try {
            BillingAccount account = repository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams
                    .builder()
                    .setCustomer(account.getCustomerId())
                    .setReturnUrl(returnUrl)
                    .build();

            return com.stripe.model.billingportal.Session.create(params).getUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create portal session", e);
        }
    }

    @Override
    public boolean handleWebhook(String payload, Map<String, String> headers) {
        String sigHeader = headers.get("stripe-signature");
        if (sigHeader == null)
            return false;

        try {
            Event event = Webhook.constructEvent(
                    payload, sigHeader, properties.getStripe().getWebhookSecret());

            // Handle events
            switch (event.getType()) {
                case "checkout.session.completed":
                case "invoice.paid":
                case "customer.subscription.updated":
                    // Sync status logic here
                    // e.g., extract customer ID, find account, update status, push to Redis
                    log.info("Handled Stripe event: {}", event.getType());
                    break;
                default:
                    log.debug("Unhandled Stripe event: {}", event.getType());
            }
            return true;
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe signature");
            return false;
        } catch (Exception e) {
            log.error("Webhook processing error", e);
            return false;
        }
    }

    private Customer getOrCreateCustomer(String accountId, String email) throws Exception {
        Optional<BillingAccount> existing = repository.findById(accountId);
        if (existing.isPresent() && existing.get().getCustomerId() != null) {
            return Customer.retrieve(existing.get().getCustomerId());
        }

        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .putMetadata("account_id", accountId)
                .build();

        Customer customer = Customer.create(params);

        // Save minimal mapping
        BillingAccount account = existing.orElse(BillingAccount.builder().accountId(accountId).email(email).build());
        account.setCustomerId(customer.getId());
        account.setProvider("stripe");
        repository.save(account);

        return customer;
    }

    private String getPriceId(String tierKey) {
        // In real impl, fetch from
        // properties.getTiers().get(tierKey).getStripePriceId()
        // For MVP, returning mock or logic
        if (properties.getTiers() != null && properties.getTiers().containsKey(tierKey)) {
            return properties.getTiers().get(tierKey).getStripePriceId();
        }
        throw new IllegalArgumentException("Invalid tier: " + tierKey);
    }
}
