package com.learning.platformservice.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Tier is required")
    private String tier;
}
