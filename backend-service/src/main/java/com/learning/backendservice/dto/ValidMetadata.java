package com.learning.backendservice.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = MetadataValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface ValidMetadata {
    String message() default "Invalid metadata: must contain required keys and valid values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
