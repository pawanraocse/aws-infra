package com.learning.authservice.sso.service;

import com.learning.authservice.sso.dto.OidcConfigRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds attribute mappings for SSO identity providers.
 * 
 * Extracted from SsoConfigurationServiceImpl to follow SRP.
 * This class handles attribute mapping configuration for different IdP types.
 * 
 * SOLID Compliance:
 * - SRP: Only handles attribute mapping logic
 * - OCP: New IdP types can be added via switch cases
 */
@Component
public class SsoAttributeMappingBuilder {

    // ========================================================================
    // SAML Attribute Mappings
    // ========================================================================

    /**
     * Build default SAML attribute mappings.
     * Maps standard SAML claims to Cognito user attributes.
     */
    public Map<String, String> buildSamlAttributeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("email", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
        mapping.put("name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
        mapping.put("custom:samlGroups", "group");
        return mapping;
    }

    /**
     * Merge custom attribute mappings with defaults.
     */
    public Map<String, String> buildSamlAttributeMapping(Map<String, String> customMappings) {
        Map<String, String> mapping = buildSamlAttributeMapping();
        if (customMappings != null) {
            mapping.putAll(customMappings);
        }
        return mapping;
    }

    // ========================================================================
    // OIDC Attribute Mappings
    // ========================================================================

    /**
     * Build default OIDC attribute mappings.
     * Maps standard OIDC claims to Cognito user attributes.
     */
    public Map<String, String> buildOidcAttributeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("email", "email");
        mapping.put("name", "name");
        mapping.put("username", "sub");
        return mapping;
    }

    /**
     * Merge custom attribute mappings with defaults.
     */
    public Map<String, String> buildOidcAttributeMapping(Map<String, String> customMappings) {
        Map<String, String> mapping = buildOidcAttributeMapping();
        if (customMappings != null) {
            mapping.putAll(customMappings);
        }
        return mapping;
    }

    // ========================================================================
    // OIDC Provider Details
    // ========================================================================

    /**
     * Build OIDC provider details for Cognito.
     * Configures issuer, scopes, and other IdP-specific settings.
     */
    public Map<String, String> buildOidcProviderDetails(OidcConfigRequest request) {
        Map<String, String> details = new HashMap<>();
        details.put("client_id", request.clientId());
        details.put("client_secret", request.clientSecret());
        details.put("authorize_scopes", request.scopes() != null ? request.scopes() : "openid email profile");
        details.put("attributes_request_method", "GET");

        switch (request.idpType()) {
            case GOOGLE -> details.put("oidc_issuer", "https://accounts.google.com");
            case AZURE_AD -> {
                if (request.issuerUrl() != null) {
                    details.put("oidc_issuer", request.issuerUrl());
                }
                details.put("authorize_scopes", "openid email profile User.Read GroupMember.Read.All");
            }
            case OKTA -> {
                details.put("oidc_issuer", request.issuerUrl());
                // Okta requires explicit email scope to return email claim
                if (request.scopes() == null || request.scopes().isBlank()) {
                    details.put("authorize_scopes", "openid email profile groups");
                }
            }
            default -> details.put("oidc_issuer", request.issuerUrl());
        }

        return details;
    }

    /**
     * Build SAML provider details for Cognito.
     */
    public Map<String, String> buildSamlProviderDetails(String metadataUrl, String metadataXml) {
        Map<String, String> details = new HashMap<>();

        if (metadataUrl != null && !metadataUrl.isBlank()) {
            details.put("MetadataURL", metadataUrl);
        } else if (metadataXml != null && !metadataXml.isBlank()) {
            details.put("MetadataFile", metadataXml);
        }

        details.put("IDPSignout", "false");
        return details;
    }
}
