package com.learning.platformservice.billing.controller;

import com.learning.platformservice.billing.config.BillingProperties;
import com.learning.platformservice.billing.service.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Stripe webhook events.
 * This endpoint is PUBLIC (no authentication) - security is via signature
 * verification.
 */
@RestController
@RequestMapping("/billing/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final BillingProperties billingProperties;
    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        if (!billingProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Billing is disabled");
        }

        try {
            // Verify webhook signature
            Event event = Webhook.constructEvent(
                    payload,
                    signature,
                    billingProperties.getStripe().getWebhookSecret());

            log.info("Received webhook: type={}, id={}", event.getType(), event.getId());

            // Process the event
            boolean processed = webhookService.processEvent(event);

            if (processed) {
                return ResponseEntity.ok("Event processed");
            } else {
                return ResponseEntity.ok("Event already processed");
            }

        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid signature");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing event");
        }
    }
}
