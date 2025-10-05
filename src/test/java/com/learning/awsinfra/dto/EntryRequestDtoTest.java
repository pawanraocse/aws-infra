package com.learning.awsinfra.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntryRequestDtoTest {
    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validKeyValue_shouldPassValidation() {
        EntryRequestDto dto = new EntryRequestDto("type", "invoice");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void blankKey_shouldFailValidation() {
        EntryRequestDto dto = new EntryRequestDto("", "invoice");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void blankValue_shouldFailValidation() {
        EntryRequestDto dto = new EntryRequestDto("type", "");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void nullKey_shouldFailValidation() {
        EntryRequestDto dto = new EntryRequestDto(null, "invoice");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void nullValue_shouldFailValidation() {
        EntryRequestDto dto = new EntryRequestDto("type", null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void longKey_shouldPassValidation() {
        String longKey = "k".repeat(255);
        EntryRequestDto dto = new EntryRequestDto(longKey, "invoice");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void longValue_shouldPassValidation() {
        String longValue = "v".repeat(255);
        EntryRequestDto dto = new EntryRequestDto("type", longValue);
        assertThat(validator.validate(dto)).isEmpty();
    }
}
