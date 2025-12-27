package com.learning.platformservice.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckoutResponse {
    private String checkoutUrl;
    private String sessionId;
}
