package com.learning.authservice.sso.dto;

import com.learning.common.dto.IdpType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for OIDC SSO configuration.
 */
public record OidcConfigRequest(
        @NotNull(message = "IdP type is required") IdpType idpType,

        @NotBlank(message = "Provider name is required") String providerName,

        @NotBlank(message = "Client ID is required") String clientId,

        @NotBlank(message = "Client secret is required") String clientSecret,

        // For generic OIDC, issuer is required
        // For Google/Azure, it can be derived
        String issuerUrl,

        // OAuth scopes (default: openid email profile)
        String scopes,

        // Attribute mappings: IdP attr -> Cognito attr
        java.util.Map<String, String> attributeMappings,

        // JIT Provisioning settings
        boolean jitProvisioningEnabled,
        String defaultRole) {
    public OidcConfigRequest {
        // For generic OIDC, issuer URL is required
        if (idpType == IdpType.OIDC && (issuerUrl == null || issuerUrl.isBlank())) {
            throw new IllegalArgumentException("Issuer URL is required for generic OIDC provider");
        }
    }
}
