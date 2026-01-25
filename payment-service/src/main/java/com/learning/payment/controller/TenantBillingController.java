package com.learning.payment.controller;

import com.learning.payment.dto.CheckoutResponse;
import com.learning.payment.dto.SubscriptionStatus;
import com.learning.payment.service.PaymentManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@Slf4j
@RequiredArgsConstructor
public class TenantBillingController {

    private final PaymentManagerService paymentManager;
    private final com.learning.payment.config.PaymentProperties properties;

    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatus> getStatus(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(paymentManager.getSubscriptionStatus(tenantId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestBody Map<String, String> request) {

        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        String tier = request.get("tier");

        // Use configured URLs or defaults from properties
        String successUrl = properties.getStripe().getSuccessUrl();
        String cancelUrl = properties.getStripe().getCancelUrl();

        // Note: Razorpay flow is client-side so these URLs might be less relevant for
        // it,
        // but we pass them for consistency or fallback.

        return ResponseEntity.ok(paymentManager.createCheckout(tenantId, null, tier, successUrl, cancelUrl));
    }

    @PostMapping("/portal")
    public ResponseEntity<Map<String, String>> createPortal(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        String returnUrl = properties.getStripe().getPortalReturnUrl();
        String url = paymentManager.createPortal(tenantId, returnUrl);
        return ResponseEntity.ok(Map.of("portalUrl", url));
    }
}
