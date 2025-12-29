package com.learning.platformservice.sso.service;

import com.learning.platformservice.sso.dto.OidcConfigRequest;
import com.learning.platformservice.sso.dto.SamlConfigRequest;
import com.learning.platformservice.sso.dto.SsoConfigDto;
import com.learning.platformservice.sso.dto.SsoTestResult;

import java.util.Optional;

/**
 * Service for managing SSO configuration per tenant.
 * Handles both SAML and OIDC identity provider setup via AWS Cognito.
 */
public interface SsoConfigurationService {

    /**
     * Get the current SSO configuration for a tenant.
     *
     * @param tenantId Tenant ID
     * @return SSO configuration if exists
     */
    Optional<SsoConfigDto> getConfiguration(String tenantId);

    /**
     * Configure SAML IdP for a tenant.
     * Creates or updates Cognito Identity Provider.
     *
     * @param tenantId Tenant ID
     * @param request  SAML configuration request
     * @return Updated SSO configuration
     */
    SsoConfigDto saveSamlConfiguration(String tenantId, SamlConfigRequest request);

    /**
     * Configure OIDC IdP for a tenant.
     * Creates or updates Cognito Identity Provider.
     *
     * @param tenantId Tenant ID
     * @param request  OIDC configuration request
     * @return Updated SSO configuration
     */
    SsoConfigDto saveOidcConfiguration(String tenantId, OidcConfigRequest request);

    /**
     * Enable or disable SSO for a tenant.
     *
     * @param tenantId Tenant ID
     * @param enabled  Whether SSO should be enabled
     * @return Updated SSO configuration
     */
    SsoConfigDto toggleSso(String tenantId, boolean enabled);

    /**
     * Delete SSO configuration and remove Cognito Identity Provider.
     *
     * @param tenantId Tenant ID
     */
    void deleteConfiguration(String tenantId);

    /**
     * Test IdP connection by validating metadata/credentials.
     *
     * @param tenantId Tenant ID
     * @return Test result with success status and details
     */
    SsoTestResult testConnection(String tenantId);

    /**
     * Get SAML Service Provider (SP) metadata XML.
     * This is used by IdPs to configure trust relationship.
     *
     * @param tenantId Tenant ID
     * @return SP metadata XML
     */
    String getSpMetadata(String tenantId);
}
