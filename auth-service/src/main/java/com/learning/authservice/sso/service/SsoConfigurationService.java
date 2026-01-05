package com.learning.authservice.sso.service;

import com.learning.authservice.sso.dto.OidcConfigRequest;
import com.learning.authservice.sso.dto.SamlConfigRequest;
import com.learning.authservice.sso.dto.SsoConfigDto;
import com.learning.authservice.sso.dto.SsoTestResult;

import java.util.Optional;

/**
 * Service interface for SSO configuration management.
 */
public interface SsoConfigurationService {

    /**
     * Get current SSO configuration for a tenant.
     */
    Optional<SsoConfigDto> getConfiguration(String tenantId);

    /**
     * Save SAML SSO configuration.
     */
    SsoConfigDto saveSamlConfiguration(String tenantId, SamlConfigRequest request);

    /**
     * Save OIDC SSO configuration.
     */
    SsoConfigDto saveOidcConfiguration(String tenantId, OidcConfigRequest request);

    /**
     * Enable or disable SSO for a tenant.
     */
    SsoConfigDto toggleSso(String tenantId, boolean enabled);

    /**
     * Delete SSO configuration for a tenant.
     */
    void deleteConfiguration(String tenantId);

    /**
     * Test SSO connection.
     */
    SsoTestResult testConnection(String tenantId);

    /**
     * Get SAML Service Provider metadata XML.
     */
    String getSpMetadata(String tenantId);

    /**
     * SSO lookup response for login page.
     */
    record SsoLookupResponse(
            boolean ssoEnabled,
            String cognitoProviderName,
            String idpType) {
    }

    /**
     * Get minimal SSO info for login redirect.
     */
    Optional<SsoLookupResponse> getSsoLookup(String tenantId);
}
