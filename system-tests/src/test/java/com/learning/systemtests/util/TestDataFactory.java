package com.learning.systemtests.util;

import com.learning.common.dto.OrganizationSignupRequest;
import com.learning.common.dto.PersonalSignupRequest;

import java.util.UUID;

import static com.learning.systemtests.config.TestConfig.DEFAULT_TEST_PASSWORD;

/**
 * Factory for generating test data with unique identifiers.
 * Ensures test isolation by using random UUIDs in test data.
 */
public class TestDataFactory {

    private TestDataFactory() {
        // Utility class
    }

    // ============================================================
    // Email Generation
    // ============================================================

    /**
     * Generate a unique test email address.
     */
    public static String randomEmail() {
        return randomEmail("test");
    }

    /**
     * Generate a unique test email with a custom prefix.
     */
    public static String randomEmail(String prefix) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "." + uniqueId + "@example.com";
    }

    /**
     * Generate a unique org admin email.
     */
    public static String randomOrgEmail() {
        return randomEmail("admin");
    }

    // ============================================================
    // Name Generation
    // ============================================================

    /**
     * Generate a unique test user name.
     */
    public static String randomName() {
        return "Test User " + UUID.randomUUID().toString().substring(0, 4);
    }

    /**
     * Generate a unique company name.
     */
    public static String randomCompanyName() {
        return "Test Corp " + UUID.randomUUID().toString().substring(0, 6);
    }

    // ============================================================
    // Signup Request Builders
    // ============================================================

    /**
     * Create a personal signup request with random data.
     */
    public static PersonalSignupRequest personalSignup() {
        return new PersonalSignupRequest(
                randomEmail(),
                DEFAULT_TEST_PASSWORD,
                randomName());
    }

    /**
     * Create a personal signup request with specified email.
     */
    public static PersonalSignupRequest personalSignup(String email) {
        return new PersonalSignupRequest(
                email,
                DEFAULT_TEST_PASSWORD,
                randomName());
    }

    /**
     * Create an organization signup request with random data.
     */
    public static OrganizationSignupRequest orgSignup() {
        return new OrganizationSignupRequest(
                randomCompanyName(),
                randomOrgEmail(),
                DEFAULT_TEST_PASSWORD,
                randomName(),
                "STANDARD");
    }

    /**
     * Create an organization signup request with specified company name.
     */
    public static OrganizationSignupRequest orgSignup(String companyName) {
        return new OrganizationSignupRequest(
                companyName,
                randomOrgEmail(),
                DEFAULT_TEST_PASSWORD,
                randomName(),
                "STANDARD");
    }

    /**
     * Create an organization signup request with specified email.
     */
    public static OrganizationSignupRequest orgSignupWithEmail(String email) {
        return new OrganizationSignupRequest(
                randomCompanyName(),
                email,
                DEFAULT_TEST_PASSWORD,
                randomName(),
                "STANDARD");
    }

    /**
     * Create an organization signup request with specified email and company.
     */
    public static OrganizationSignupRequest orgSignupWithEmail(String email, String companyName) {
        return new OrganizationSignupRequest(
                companyName,
                email,
                DEFAULT_TEST_PASSWORD,
                randomName(),
                "STANDARD");
    }

    // ============================================================
    // Entry Data
    // ============================================================

    /**
     * Create a test entry JSON.
     */
    public static String entryJson(String title, String content) {
        return String.format("""
                {
                    "title": "%s",
                    "content": "%s"
                }
                """, title, content);
    }

    /**
     * Create a random test entry JSON.
     */
    public static String randomEntryJson() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return entryJson(
                "Test Entry " + uniqueId,
                "Content for test entry " + uniqueId);
    }

    // ============================================================
    // Invitation Data
    // ============================================================

    /**
     * Create an invitation request JSON.
     */
    public static String invitationJson(String email, String roleId) {
        return String.format("""
                {
                    "email": "%s",
                    "roleId": "%s"
                }
                """, email, roleId);
    }
}
