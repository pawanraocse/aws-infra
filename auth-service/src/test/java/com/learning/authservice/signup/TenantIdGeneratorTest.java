package com.learning.authservice.signup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantIdGenerator.
 * Tests the slugification logic without WebClient mocking.
 */
class TenantIdGeneratorTest {

    private TenantIdGenerator generator;

    @BeforeEach
    void setUp() {
        // Create with null WebClient - we'll test slugify directly
        generator = new TenantIdGenerator(null);
    }

    @Test
    @DisplayName("Personal tenant ID includes user prefix and timestamp")
    void personalTenantId_includesUserPrefixAndTimestamp() {
        // Given
        PersonalSignupData request = new PersonalSignupData("john.doe@example.com", "password", "John Doe");

        // When
        String tenantId = generator.generate(request);

        // Then
        assertThat(tenantId).startsWith("user-johndoe-");
        assertThat(tenantId).matches("user-johndoe-\\d+");
    }

    @Test
    @DisplayName("Slugify handles basic company name")
    void slugify_handlesBasicName() {
        String result = invokeSlugify("Acme Inc");
        assertThat(result).isEqualTo("acme-inc");
    }

    @Test
    @DisplayName("Slugify handles special characters correctly")
    void slugify_handlesSpecialCharacters() {
        String result = invokeSlugify("O'Brien & Sons, LLC!");
        assertThat(result).isEqualTo("o-brien-sons-llc");
    }

    @Test
    @DisplayName("Slugify handles uppercase and spaces")
    void slugify_handlesUppercaseAndSpaces() {
        String result = invokeSlugify("ACME Corporation");
        assertThat(result).isEqualTo("acme-corporation");
    }

    @Test
    @DisplayName("Slugify handles leading/trailing special chars")
    void slugify_handlesLeadingTrailingChars() {
        String result = invokeSlugify("---Test Company---");
        assertThat(result).isEqualTo("test-company");
    }

    @Test
    @DisplayName("Slugify handles multiple consecutive special chars")
    void slugify_handlesMultipleSpecialChars() {
        String result = invokeSlugify("Company   &&&   Name");
        assertThat(result).isEqualTo("company-name");
    }

    @Test
    @DisplayName("Different company names that could collide")
    void slugify_collisionExamples() {
        // These all produce "acme-inc"
        assertThat(invokeSlugify("Acme Inc")).isEqualTo("acme-inc");
        assertThat(invokeSlugify("ACME Inc")).isEqualTo("acme-inc");
        assertThat(invokeSlugify("Acme, Inc.")).isEqualTo("acme-inc");
        assertThat(invokeSlugify("Acme Inc!")).isEqualTo("acme-inc");
    }

    private String invokeSlugify(String input) {
        // Use reflection to test private slugify method
        try {
            java.lang.reflect.Method method = TenantIdGenerator.class.getDeclaredMethod("slugify", String.class);
            method.setAccessible(true);
            return (String) method.invoke(generator, input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke slugify", e);
        }
    }
}
