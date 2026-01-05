package com.learning.authservice.sso.dto;

import com.learning.common.dto.IdpType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * DTO for SSO configuration.
 * Used for both request and response in SSO config API.
 */
@Data
@Builder
public class SsoConfigDto {

    private String tenantId;
    private boolean ssoEnabled;

    @NotNull(message = "IdP type is required")
    private IdpType idpType;

    // Common fields
    private String providerName;
    private Map<String, String> attributeMappings;

    // SAML-specific fields
    private String samlMetadataUrl;
    private String samlMetadataXml;
    private String samlEntityId;
    private String samlSsoUrl;
    private String samlCertificate;

    // OIDC-specific fields
    private String oidcIssuer;
    private String oidcClientId;
    // Note: Client secret is write-only (never returned in responses)
    private String oidcClientSecret;
    private String oidcScopes;

    // JIT Provisioning
    private boolean jitProvisioningEnabled;
    private String defaultRole;

    // Cognito Identity Provider name
    private String cognitoProviderName;

    // Test status
    private String lastTestedAt;
    private String testStatus;

    // Audit fields
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
