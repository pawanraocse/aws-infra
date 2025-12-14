package com.learning.authservice.signup;

import org.springframework.stereotype.Component;

/**
 * Strategy for generating tenant IDs based on signup type.
 * Uses pattern matching to handle different signup types.
 */
@Component
public class TenantIdGenerator {

    /**
     * Generate a tenant ID based on signup type.
     * Personal: user-{sanitized-username}-{timestamp}
     * Organization: slugified company name
     */
    public String generate(SignupRequest request) {
        return switch (request) {
            case PersonalSignupData p -> generatePersonalTenantId(p.email());
            case OrganizationSignupData o -> slugify(o.companyName());
        };
    }

    private String generatePersonalTenantId(String email) {
        String username = email.split("@")[0];
        String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return "user-" + sanitized + "-" + timestamp;
    }

    private String slugify(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
