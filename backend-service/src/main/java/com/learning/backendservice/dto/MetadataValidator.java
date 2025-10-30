package com.learning.backendservice.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Set;

public class MetadataValidator implements ConstraintValidator<ValidMetadata, Map<String, Object>> {
    private static final Set<String> REQUIRED_KEYS = Set.of("type", "amount");
    private static final Set<String> ALLOWED_TYPES = Set.of("invoice", "payment");

    @Override
    public boolean isValid(Map<String, Object> metadata, ConstraintValidatorContext context) {
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        for (String key : REQUIRED_KEYS) {
            if (!metadata.containsKey(key)) {
                return false;
            }
        }
        Object typeObj = metadata.get("type");
        if (!(typeObj instanceof String type) || type.isBlank() || !ALLOWED_TYPES.contains(type)) {
            return false;
        }
        Object amountObj = metadata.get("amount");
        if (!(amountObj instanceof Integer amount) || amount <= 0) {
            return false;
        }
        return true;
    }
}
