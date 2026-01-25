package com.learning.payment.controller;

import com.learning.payment.dto.CheckoutRequest;
import com.learning.payment.dto.CheckoutResponse;
import com.learning.payment.service.PaymentManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentManagerService paymentManager;

    @PostMapping("/{accountId}/checkout")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @PathVariable String accountId,
            @RequestParam(required = false) String email,
            @RequestBody CheckoutRequest request) {
        
        log.info("Creating checkout for account: {}", accountId);
        CheckoutResponse response = paymentManager.createCheckout(
                accountId, 
                email, 
                request.getTier(), 
                request.getSuccessUrl(), 
                request.getCancelUrl()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/portal")
    public ResponseEntity<Map<String, String>> createPortal(
            @PathVariable String accountId,
            @RequestParam(required = false) String returnUrl) {
        
        String url = paymentManager.createPortal(accountId, returnUrl);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/hooks/{provider}")
    public ResponseEntity<Void> listWebhook(
            @PathVariable String provider,
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {
        
        try {
            paymentManager.handleWebhook(provider, payload, headers);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
