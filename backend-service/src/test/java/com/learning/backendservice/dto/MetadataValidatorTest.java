package com.learning.backendservice.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetadataValidator.
 * Tests validation rules for metadata map with required keys and allowed
 * values.
 */
class MetadataValidatorTest {

    private MetadataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MetadataValidator();
    }

    @Test
    void isValid_WithValidInvoiceMetadata_ReturnsTrue() {
        // Given
        Map<String, Object> metadata = Map.of(
                "type", "invoice",
                "amount", 100);

        // When/Then
        assertTrue(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithValidPaymentMetadata_ReturnsTrue() {
        // Given
        Map<String, Object> metadata = Map.of(
                "type", "payment",
                "amount", 500);

        // When/Then
        assertTrue(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithNullMetadata_ReturnsFalse() {
        // When/Then
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void isValid_WithEmptyMetadata_ReturnsFalse() {
        // When/Then
        assertFalse(validator.isValid(Map.of(), null));
    }

    @Test
    void isValid_WithMissingType_ReturnsFalse() {
        // Given
        Map<String, Object> metadata = Map.of("amount", 100);

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithMissingAmount_ReturnsFalse() {
        // Given
        Map<String, Object> metadata = Map.of("type", "invoice");

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithInvalidType_ReturnsFalse() {
        // Given - type not in allowed list
        Map<String, Object> metadata = Map.of(
                "type", "unknown",
                "amount", 100);

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithBlankType_ReturnsFalse() {
        // Given
        Map<String, Object> metadata = Map.of(
                "type", "   ",
                "amount", 100);

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithNonStringType_ReturnsFalse() {
        // Given - type is not a String
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", 123);
        metadata.put("amount", 100);

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithZeroAmount_ReturnsFalse() {
        // Given
        Map<String, Object> metadata = Map.of(
                "type", "invoice",
                "amount", 0);

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithNegativeAmount_ReturnsFalse() {
        // Given
        Map<String, Object> metadata = Map.of(
                "type", "invoice",
                "amount", -50);

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithNonIntegerAmount_ReturnsFalse() {
        // Given - amount is not an Integer
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "invoice");
        metadata.put("amount", "100");

        // When/Then
        assertFalse(validator.isValid(metadata, null));
    }

    @Test
    void isValid_WithExtraKeys_ReturnsTrue() {
        // Given - additional keys should be allowed
        Map<String, Object> metadata = Map.of(
                "type", "payment",
                "amount", 200,
                "description", "Test payment",
                "reference", "REF-123");

        // When/Then
        assertTrue(validator.isValid(metadata, null));
    }
}
