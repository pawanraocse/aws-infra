package com.learning.authservice.sso.dto;

import com.learning.common.dto.IdpType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for SAML SSO configuration.
 */
public record SamlConfigRequest(
        @NotNull(message = "IdP type is required") IdpType idpType,

        @NotBlank(message = "Provider name is required") String providerName,

        // Either metadataUrl OR metadataXml must be provided
        String metadataUrl,
        String metadataXml,

        // Optional: Override values from metadata
        String entityId,
        String ssoUrl,
        String certificate,

        // Attribute mappings: IdP attr -> Cognito attr
        java.util.Map<String, String> attributeMappings,

        // JIT Provisioning settings
        boolean jitProvisioningEnabled,
        String defaultRole) {
    public SamlConfigRequest {
        // Validation: either metadataUrl or metadataXml must be provided
        if ((metadataUrl == null || metadataUrl.isBlank())
                && (metadataXml == null || metadataXml.isBlank())) {
            // Allow if entityId and ssoUrl are provided (manual config)
            if (entityId == null || entityId.isBlank() || ssoUrl == null || ssoUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "Either metadataUrl, metadataXml, or (entityId + ssoUrl) must be provided");
            }
        }
    }
}
