package com.learning.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {
    @NotBlank
    private String tier; // e.g., "starter", "pro"
    private String successUrl;
    private String cancelUrl;
}
