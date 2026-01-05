package com.learning.common.dto;

/**
 * Identity Provider (IdP) type enumeration.
 * Supports various enterprise SSO providers for B2B tenants.
 */
public enum IdpType {
    COGNITO_DEFAULT, // Standard Cognito authentication
    SAML, // Generic SAML 2.0 provider
    OIDC, // Generic OpenID Connect provider
    GOOGLE, // Google Workspace (OIDC - no groups)
    GOOGLE_SAML, // Google Workspace (SAML - with group mapping)
    AZURE_AD, // Microsoft Azure Active Directory
    OKTA, // Okta
    PING // Ping Identity
}
